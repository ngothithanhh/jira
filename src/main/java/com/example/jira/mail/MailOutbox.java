package com.example.jira.mail;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MailOutbox {
    public enum Kind {
        REGISTRATION_OTP("REGISTRATION"), ACCOUNT_EMAIL_OTP("ACCOUNT_EMAIL"), PROJECT_INVITATION("PROJECT_INVITATION");
        final String aggregateType;
        Kind(String aggregateType) { this.aggregateType = aggregateType; }
    }

    enum Status { PROVIDER_ACCEPTED, RETRY_WAIT, UNKNOWN, FAILED }
    record Lease(UUID id, UUID leaseId) { }
    record Dispatch(MailPayloadCipher.Context context, byte[] ciphertext, byte[] nonce, int attempt, Instant expiresAt) { }

    private final JdbcTemplate db;
    private final MailPayloadCipher cipher;

    MailOutbox(JdbcTemplate db, MailPayloadCipher cipher) { this.db = db; this.cipher = cipher; }

    /** Transaction nghiệp vụ sở hữu aggregateId/generation. Rollback của caller cũng hủy mail mới. */
    public Optional<UUID> enqueue(Kind kind, UUID aggregateId, int generation, MailMessage message, Instant expiresAt) {
        requireBusinessTransaction();
        Objects.requireNonNull(aggregateId);
        if (generation < 1) throw new IllegalArgumentException("Generation mail phải dương");
        UUID id = UUID.randomUUID();
        var context = new MailPayloadCipher.Context(id, kind, aggregateId, generation, message.recipient(), cipher.version());
        var sealed = cipher.seal(context, message);
        return db.query("""
                INSERT INTO mail_outbox (id,kind,aggregate_type,aggregate_id,generation,recipient,payload_ciphertext,nonce,
                    key_version,status,next_attempt_at,expires_at,created_at,updated_at)
                SELECT ?,?,?,?,?,?,?,?,?,'QUEUED',clock_timestamp(),?,clock_timestamp(),clock_timestamp()
                WHERE ? > clock_timestamp()
                ON CONFLICT (aggregate_type,aggregate_id,generation,kind) DO NOTHING RETURNING id
                """, (rs, row) -> rs.getObject("id", UUID.class), id, kind.name(), kind.aggregateType, aggregateId, generation,
                message.recipient(), sealed.ciphertext(), sealed.nonce(), cipher.version(), utc(expiresAt), utc(expiresAt)).stream().findFirst();
    }

    /** Resend/revoke hủy job chưa dispatch và chặn retry của job đang gửi, trong transaction aggregate. */
    public int supersedeThrough(Kind kind, UUID aggregateId, int generation) {
        requireBusinessTransaction();
        return db.update("""
                UPDATE mail_outbox SET superseded=true,
                    status=CASE WHEN status='SENDING' AND dispatch_started_at IS NOT NULL THEN status ELSE 'SUPERSEDED' END,
                    payload_ciphertext=CASE WHEN status='SENDING' AND dispatch_started_at IS NOT NULL THEN payload_ciphertext END,
                    nonce=CASE WHEN status='SENDING' AND dispatch_started_at IS NOT NULL THEN nonce END,
                    lease_id=CASE WHEN status='SENDING' AND dispatch_started_at IS NOT NULL THEN lease_id END,
                    lease_until=CASE WHEN status='SENDING' AND dispatch_started_at IS NOT NULL THEN lease_until END,
                    updated_at=clock_timestamp()
                WHERE kind=? AND aggregate_type=? AND aggregate_id=? AND generation<=?
                    AND status IN ('QUEUED','RETRY_WAIT','SENDING') AND NOT superseded
                """, kind.name(), kind.aggregateType, aggregateId, generation);
    }

    /** Một câu SQL chọn và claim cùng hàng. Mỗi worker chỉ giữ một lease và xử lý ngoài transaction. */
    Optional<Lease> claim() {
        UUID lease = UUID.randomUUID();
        return db.query("""
                WITH candidate AS (
                    SELECT id FROM mail_outbox WHERE status IN ('QUEUED','RETRY_WAIT')
                        AND next_attempt_at<=clock_timestamp() AND expires_at>clock_timestamp()
                    ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE mail_outbox m SET status='SENDING', lease_id=?, lease_until=clock_timestamp()+interval '90 seconds',
                    dispatch_started_at=NULL, updated_at=clock_timestamp()
                FROM candidate c WHERE m.id=c.id RETURNING m.id
                """, (rs, row) -> new Lease(rs.getObject("id", UUID.class), lease), lease).stream().findFirst();
    }

    /** Chốt mốc dispatch trước khi mở payload. Supersede thắng trước mốc này thì worker không nhận nội dung. */
    Optional<Dispatch> beginDispatch(Lease lease) {
        return db.query("""
                UPDATE mail_outbox SET dispatch_started_at=clock_timestamp(), attempt_count=attempt_count+1, updated_at=clock_timestamp()
                WHERE id=? AND lease_id=? AND status='SENDING' AND dispatch_started_at IS NULL
                    AND NOT superseded AND expires_at>clock_timestamp() AND lease_until>clock_timestamp()
                RETURNING id,kind,aggregate_id,generation,recipient,key_version,payload_ciphertext,nonce,attempt_count,expires_at
                """, (rs, row) -> new Dispatch(new MailPayloadCipher.Context(rs.getObject("id", UUID.class),
                Kind.valueOf(rs.getString("kind")), rs.getObject("aggregate_id", UUID.class), rs.getInt("generation"),
                rs.getString("recipient"), rs.getInt("key_version")), rs.getBytes("payload_ciphertext"), rs.getBytes("nonce"),
                rs.getInt("attempt_count"), rs.getObject("expires_at", OffsetDateTime.class).toInstant()), lease.id(), lease.leaseId()).stream().findFirst();
    }

    MailMessage decrypt(Dispatch job) { return cipher.open(job.context(), job.ciphertext(), job.nonce()); }

    /** Sau refresh và ngay trước mỗi POST, đọc lại lease, hạn và dấu supersede. */
    boolean canDispatch(Lease lease) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM mail_outbox WHERE id=? AND lease_id=? AND status='SENDING'
                    AND dispatch_started_at IS NOT NULL AND NOT superseded
                    AND expires_at>clock_timestamp() AND lease_until>clock_timestamp())
                """, Boolean.class, lease.id(), lease.leaseId()));
    }

    /** Chỉ lease hiện hành được ghi kết quả. Trạng thái cuối xóa nội dung mã hóa ngay, giữ metadata đối chiếu. */
    boolean finish(Lease lease, Status status, MailSender.Code code, String providerId, Instant next) {
        boolean retry = status == Status.RETRY_WAIT;
        return db.update("""
                UPDATE mail_outbox SET status=CASE WHEN superseded AND ? THEN 'SUPERSEDED' ELSE ? END,
                    provider_message_id=?, redacted_error_code=?,
                    next_attempt_at=COALESCE(?,next_attempt_at),
                    payload_ciphertext=CASE WHEN ? AND NOT superseded THEN payload_ciphertext END,
                    nonce=CASE WHEN ? AND NOT superseded THEN nonce END,
                    lease_id=NULL, lease_until=NULL, updated_at=clock_timestamp()
                WHERE id=? AND lease_id=? AND status='SENDING' AND dispatch_started_at IS NOT NULL AND lease_until>clock_timestamp()
                """, retry || code == MailSender.Code.INELIGIBLE, status.name(), providerId, code == null ? null : code.name(), next == null ? null : utc(next),
                retry, retry, lease.id(), lease.leaseId()) == 1;
    }

    /** Lease hết hạn không được gửi lại. Job hết hạn trước dispatch được dọn cả khi provider dừng. */
    void reap() {
        db.update("""
                WITH stale AS (
                    SELECT id,status='SENDING' AND lease_until<=clock_timestamp() AS lost_lease
                    FROM mail_outbox
                    WHERE status='SENDING' AND lease_until<=clock_timestamp()
                        OR expires_at<=clock_timestamp() AND (status IN ('QUEUED','RETRY_WAIT') OR status='SENDING' AND dispatch_started_at IS NULL)
                    ORDER BY expires_at,id LIMIT 64 FOR UPDATE SKIP LOCKED
                )
                UPDATE mail_outbox m SET status=CASE WHEN stale.lost_lease THEN 'UNKNOWN' ELSE 'FAILED' END,
                    redacted_error_code=CASE WHEN stale.lost_lease THEN 'LEASE_EXPIRED' ELSE 'EXPIRED' END,
                    lease_id=NULL, lease_until=NULL, payload_ciphertext=NULL, nonce=NULL, updated_at=clock_timestamp()
                FROM stale WHERE m.id=stale.id
                """);
    }

    private static OffsetDateTime utc(Instant time) { return time.atOffset(ZoneOffset.UTC); }

    private void requireBusinessTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(db.getDataSource()))) {
            throw new IllegalStateException("Mail outbox cần transaction nghiệp vụ trên cùng datasource");
        }
    }
}
