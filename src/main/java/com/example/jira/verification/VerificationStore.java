package com.example.jira.verification;

import com.example.jira.mail.MailOutbox.Kind;
import com.example.jira.mail.MailOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
class VerificationStore {
    enum Flow {
        REGISTRATION("registration_challenges", Kind.REGISTRATION_OTP),
        ACCOUNT("email_verification_challenges", Kind.ACCOUNT_EMAIL_OTP);

        final String table;
        final Kind mailKind;
        Flow(String table, Kind mailKind) { this.table = table; this.mailKind = mailKind; }
    }
    enum State { PENDING, LOCKED, CONSUMED, EXPIRED }
    record Pending(String userName, String fullName, String passwordHash) {
        @Override public String toString() { return "Pending[redacted]"; }
    }
    record Challenge(UUID id, String email, byte[] proof, int generation, byte[] otp, byte[] history,
                     Instant expires, Instant proofExpires, Instant resendAfter, int attempts, State state, Pending pending) {
        @Override public String toString() { return "Challenge[redacted]"; }
    }
    record Account(int id, String email, boolean enabled, long authVersion, Instant verifiedAt) { }
    record Rate(byte[] subject, String action, Instant window) { }

    private final JdbcTemplate db;
    VerificationStore(JdbcTemplate db) { this.db = db; }

    /** Hàng theo email chuẩn là điểm khóa, kể cả hai lượt start đầu tiên chạy đồng thời. */
    Challenge reserveRegistration(String email, Instant now) {
        return db.query("""
                INSERT INTO registration_challenges(email_normalized,registration_id,generation,expires_at,
                    proof_expires_at,resend_after,attempt_count,state,created_at,updated_at)
                VALUES (?,?,0,?,?,?,0,'EXPIRED',?,?)
                ON CONFLICT (email_normalized) DO UPDATE SET email_normalized=EXCLUDED.email_normalized
                RETURNING *
                """, mapper(Flow.REGISTRATION), email, UUID.randomUUID(), time(now), time(now), time(now), time(now), time(now)).get(0);
    }

    Optional<Challenge> registration(UUID id, boolean lock) {
        return db.query("SELECT * FROM registration_challenges WHERE registration_id=?" + (lock ? " FOR UPDATE" : ""),
                mapper(Flow.REGISTRATION), id).stream().findFirst();
    }

    Optional<Challenge> accountChallenge(int userId, boolean lock) {
        return db.query("SELECT * FROM email_verification_challenges WHERE user_id=?" + (lock ? " FOR UPDATE" : ""),
                mapper(Flow.ACCOUNT), userId).stream().findFirst();
    }

    Optional<Account> account(int userId, boolean lock) {
        return db.query("SELECT user_id,email,enabled,auth_version,email_verified_at FROM users WHERE user_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> {
                    var verifiedAt = rs.getObject("email_verified_at", OffsetDateTime.class);
                    return new Account(rs.getInt("user_id"), rs.getString("email"), rs.getBoolean("enabled"),
                            rs.getLong("auth_version"), verifiedAt == null ? null : verifiedAt.toInstant());
                }, userId).stream().findFirst();
    }

    private static RowMapper<Challenge> mapper(Flow flow) {
        return (rs, row) -> new Challenge(rs.getObject("registration_id", UUID.class), rs.getString("email_normalized"),
                rs.getBytes("proof_hash"), rs.getInt("generation"), rs.getBytes("otp_hmac"),
                flow == Flow.REGISTRATION ? rs.getBytes("otp_history") : new byte[0],
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("proof_expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("resend_after", OffsetDateTime.class).toInstant(), rs.getInt("attempt_count"),
                State.valueOf(rs.getString("state")),
                flow == Flow.REGISTRATION ? new Pending(rs.getString("username"), rs.getString("full_name"),
                        rs.getString("password_hash")) : null);
    }

    /** Start đổi toàn bộ hồ sơ pending. Resend chuyển lại cùng hồ sơ dưới khóa của hàng email. */
    void saveRegistration(Challenge next, Instant now) {
        db.update("""
                UPDATE registration_challenges SET registration_id=?,proof_hash=?,generation=?,username=?,full_name=?,
                    password_hash=?,otp_hmac=?,otp_history=?,expires_at=?,proof_expires_at=?,resend_after=?,attempt_count=0,state='PENDING',
                    verified_user_id=NULL,updated_at=? WHERE email_normalized=?
                """, next.id(), next.proof(), next.generation(), next.pending().userName(), next.pending().fullName(),
                next.pending().passwordHash(), next.otp(), next.history(), time(next.expires()), time(next.proofExpires()), time(next.resendAfter()),
                time(now), next.email());
    }

    void saveAccount(int userId, Challenge next, Instant now) {
        db.update("""
                INSERT INTO email_verification_challenges(user_id,email_normalized,registration_id,proof_hash,generation,
                    otp_hmac,expires_at,proof_expires_at,resend_after,attempt_count,state,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,0,'PENDING',?,?)
                ON CONFLICT (user_id) DO UPDATE SET email_normalized=EXCLUDED.email_normalized,
                    registration_id=EXCLUDED.registration_id,proof_hash=EXCLUDED.proof_hash,generation=EXCLUDED.generation,
                    otp_hmac=EXCLUDED.otp_hmac,expires_at=EXCLUDED.expires_at,proof_expires_at=EXCLUDED.proof_expires_at,
                    resend_after=EXCLUDED.resend_after,attempt_count=0,state='PENDING',updated_at=EXCLUDED.updated_at
                """, userId, next.email(), next.id(), next.proof(), next.generation(), next.otp(), time(next.expires()),
                time(next.proofExpires()), time(next.resendAfter()), time(now), time(now));
    }

    boolean emailExists(String email) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM users WHERE lower(btrim(email) COLLATE "C")=?)
                """, Boolean.class, email));
    }

    /** Unique index là chốt cuối nếu có tiến trình khác tạo user trong lúc giữ khóa challenge. */
    Optional<Integer> createUser(Challenge challenge, Instant now) {
        Pending pending = challenge.pending();
        return db.query("""
                INSERT INTO users(email,username,full_name,password_hash,enabled,auth_version,created_at,email_verified_at)
                VALUES (?,?,?,?,true,0,?,?)
                ON CONFLICT (lower(btrim(email) COLLATE "C")) DO NOTHING RETURNING user_id
                """, (rs, row) -> rs.getInt(1), challenge.email(), pending.userName(), pending.fullName(),
                pending.passwordHash(), time(now).toLocalDateTime(), time(now)).stream().findFirst();
    }

    void verifyAccount(int userId, Instant now) {
        db.update("UPDATE users SET email_verified_at=? WHERE user_id=?", time(now), userId);
    }

    /**
     * Ghi audit USER sau xác minh trong transaction hiện tại; actor null cho đăng ký public.
     * created_at không có offset nên dùng giờ JVM như AuditLogService, tránh lệch ngày khi lọc chung.
     * Các hạn OTP vẫn dùng Instant/UTC qua time(), không đổi cách xét hết hạn.
     */
    void audit(Integer actorId, int userId, Instant now) {
        db.update("""
                INSERT INTO audit_logs(user_id,action,entity_type,entity_id,description,created_at)
                VALUES (?,'VERIFY_EMAIL','USER',?,'Xác minh địa chỉ email',?)
                """, actorId, userId, LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    void failedAttempt(Flow flow, Challenge challenge, Instant now) {
        int next = challenge.attempts() + 1;
        if (next == 5) {
            finish(flow, challenge.id(), State.LOCKED, null, next, now);
            return;
        }
        db.update("UPDATE " + flow.table + " SET attempt_count=?,updated_at=? WHERE registration_id=?",
                next, time(now), challenge.id());
    }

    /** Xóa hash mật khẩu và OTP khi kết thúc. Giữ proof tới TTL để status/verify idempotent. */
    void finish(Flow flow, UUID id, State state, Integer userId, int attempts, Instant now) {
        if (flow == Flow.REGISTRATION) {
            db.update("""
                    UPDATE registration_challenges SET state=?,verified_user_id=?,attempt_count=?,otp_hmac=NULL,otp_history=''::bytea,
                        username=NULL,full_name=NULL,password_hash=NULL,updated_at=? WHERE registration_id=?
                    """, state.name(), userId, attempts, time(now), id);
        } else {
            db.update("""
                    UPDATE email_verification_challenges SET state=?,attempt_count=?,otp_hmac=NULL,updated_at=?
                    WHERE registration_id=?
                    """, state.name(), attempts, time(now), id);
        }
    }

    /** Giữ khóa cửa sổ PostgreSQL cho cả lượt so giới hạn và tăng counter. */
    int lockRate(Rate key) {
        db.update("""
                INSERT INTO verification_rate_limits(subject_hash,action,window_start,attempts)
                VALUES (?,?,?,0) ON CONFLICT DO NOTHING
                """, key.subject(), key.action(), time(key.window()));
        return db.queryForObject("""
                SELECT attempts FROM verification_rate_limits WHERE subject_hash=? AND action=? AND window_start=? FOR UPDATE
                """, Integer.class, key.subject(), key.action(), time(key.window()));
    }

    void incrementRate(Rate key) {
        db.update("""
                UPDATE verification_rate_limits SET attempts=attempts+1 WHERE subject_hash=? AND action=? AND window_start=?
                """, key.subject(), key.action(), time(key.window()));
    }

    void supersede(Flow flow, Challenge challenge) {
        MailOutbox.supersedeThrough(db, flow.mailKind, challenge.id(), challenge.generation());
    }

    String deliveryState(Flow flow, Challenge challenge) {
        return MailOutbox.deliveryState(db, flow.mailKind, challenge.id(), challenge.generation()).orElse(null);
    }

    boolean installed() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass('registration_challenges') IS NOT NULL", Boolean.class));
    }

    /** Dọn từng nhóm hàng nhỏ, bỏ qua challenge đang có transaction nghiệp vụ giữ khóa. */
    void clean(Instant now) {
        for (Flow flow : Flow.values()) {
            String pending = flow == Flow.REGISTRATION ? ",username=NULL,full_name=NULL,password_hash=NULL,otp_history=''::bytea" : "";
            db.update("UPDATE " + flow.table + " SET state='EXPIRED',otp_hmac=NULL,updated_at=?" + pending
                    + " WHERE registration_id IN (SELECT registration_id FROM " + flow.table
                    + " WHERE proof_expires_at<=? AND state IN ('PENDING','LOCKED')"
                    + " ORDER BY proof_expires_at LIMIT 256 FOR UPDATE SKIP LOCKED)", time(now), time(now));
            db.update("DELETE FROM " + flow.table + " WHERE registration_id IN (SELECT registration_id FROM "
                    + flow.table + " WHERE proof_expires_at<=? ORDER BY proof_expires_at LIMIT 256 FOR UPDATE SKIP LOCKED)",
                    time(now.minusSeconds(86400)));
        }
        db.update("""
                DELETE FROM verification_rate_limits WHERE (subject_hash,action,window_start) IN (
                    SELECT subject_hash,action,window_start FROM verification_rate_limits WHERE window_start<?
                    ORDER BY window_start LIMIT 512 FOR UPDATE SKIP LOCKED)
                """, time(now.minusSeconds(7200)));
    }

    private static OffsetDateTime time(Instant now) { return now.atOffset(ZoneOffset.UTC); }
}
