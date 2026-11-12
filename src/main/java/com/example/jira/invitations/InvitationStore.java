package com.example.jira.invitations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class InvitationStore {
    enum State { PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED }

    record Invitation(UUID id, int projectId, Integer inviterId, String email, int roleId, State state,
                      byte[] tokenHash, int generation, Instant expiresAt, Instant resendAfter,
                      Instant createdAt, Integer acceptedUserId, String projectName, String projectKey,
                      String roleName, String inviterName, String deliveryState, String deliveryError) {
        State currentState(Instant now) {
            return state == State.PENDING && !now.isBefore(expiresAt) ? State.EXPIRED : state;
        }
    }

    private static final String SELECT = """
            SELECT i.*,p.project_name,p.project_key,r.role_name,
                coalesce(nullif(u.full_name,''),u.username) inviter_name,o.status delivery_state,
                CASE WHEN o.redacted_error_code IN ('BREVO_AUTH','MAIL_REAUTH') THEN 'MAIL_REAUTH' END delivery_error
            FROM project_invitations i
            JOIN projects p ON p.project_id=i.project_id JOIN user_roles r ON r.role_id=i.role_id
            LEFT JOIN users u ON u.user_id=i.inviter_user_id
            LEFT JOIN mail_outbox o ON o.aggregate_type='PROJECT_INVITATION' AND o.kind='PROJECT_INVITATION'
                 AND o.aggregate_id=i.invitation_id AND o.generation=i.generation
            """;
    private static final RowMapper<Invitation> MAP = (rs, row) -> new Invitation(
            rs.getObject("invitation_id", UUID.class), rs.getInt("project_id"), rs.getObject("inviter_user_id", Integer.class),
            rs.getString("email_normalized"), rs.getInt("role_id"), State.valueOf(rs.getString("state")),
            rs.getBytes("token_hash"), rs.getInt("generation"), instant(rs.getObject("expires_at", OffsetDateTime.class)),
            instant(rs.getObject("resend_after", OffsetDateTime.class)), instant(rs.getObject("created_at", OffsetDateTime.class)),
            rs.getObject("accepted_user_id", Integer.class), rs.getString("project_name"), rs.getString("project_key"),
            rs.getString("role_name"), rs.getString("inviter_name"), rs.getString("delivery_state"), rs.getString("delivery_error"));

    private final JdbcTemplate db;
    InvitationStore(JdbcTemplate db) { this.db = db; }

    Optional<Integer> projectId(UUID id) {
        return db.query("SELECT project_id FROM project_invitations WHERE invitation_id=?", (rs, row) -> rs.getInt(1), id)
                .stream().findFirst();
    }

    Optional<Invitation> find(int projectId, UUID id, boolean lock) {
        return db.query(SELECT + " WHERE i.project_id=? AND i.invitation_id=?" + (lock ? " FOR UPDATE OF i" : ""),
                MAP, projectId, id).stream().findFirst();
    }

    Optional<Invitation> pending(int projectId, String email) {
        return db.query(SELECT + " WHERE i.project_id=? AND i.email_normalized=? AND i.state='PENDING' FOR UPDATE OF i",
                MAP, projectId, email).stream().findFirst();
    }

    List<Invitation> forProject(int projectId, State state, Instant now, int offset, int limit) {
        String order = " ORDER BY i.created_at DESC,i.invitation_id DESC LIMIT ? OFFSET ?";
        if (state == null) return db.query(SELECT + " WHERE i.project_id=?" + order, MAP, projectId, limit, offset);
        return db.query(SELECT + """
                 WHERE i.project_id=?
                 AND CASE WHEN i.state='PENDING' AND i.expires_at<=? THEN 'EXPIRED' ELSE i.state END=?
                """ + order, MAP, projectId, time(now), state.name(), limit, offset);
    }

    List<Invitation> forRecipient(String email, int userId, int offset, int limit) {
        return db.query(SELECT + """
                 WHERE i.email_normalized=? AND (i.state<>'ACCEPTED' OR i.accepted_user_id=?)
                 ORDER BY i.created_at DESC,i.invitation_id DESC LIMIT ? OFFSET ?
                """, MAP, email, userId, limit, offset);
    }

    void insert(UUID id, int projectId, int inviter, Integer invited, String email, int roleId, byte[] tokenHash,
                Instant now, Instant expires, Instant resendAfter) {
        db.update("""
                INSERT INTO project_invitations(invitation_id,project_id,inviter_user_id,invited_user_id,email_normalized,
                    role_id,state,token_hash,generation,expires_at,resend_after,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',?,1,?,?,?,?)
                """, id, projectId, inviter, invited, email, roleId, tokenHash, time(expires), time(resendAfter), time(now), time(now));
    }

    void rotate(Invitation invite, byte[] tokenHash, Instant expires, Instant resendAfter, Instant now) {
        db.update("""
                UPDATE project_invitations SET token_hash=?,generation=generation+1,expires_at=?,resend_after=?,updated_at=?
                WHERE invitation_id=?
                """, tokenHash, time(expires), time(resendAfter), time(now), invite.id());
    }

    void decide(Invitation invite, State state, Integer acceptedUser, Instant now) {
        db.update("UPDATE project_invitations SET state=?,accepted_user_id=?,decided_at=?,updated_at=? WHERE invitation_id=?",
                state.name(), acceptedUser, time(now), time(now), invite.id());
        com.example.jira.mail.MailOutbox.supersedeThrough(db, com.example.jira.mail.MailOutbox.Kind.PROJECT_INVITATION,
                invite.id(), invite.generation());
    }

    boolean activeUser(int userId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE user_id=? AND enabled)",
                Boolean.class, userId));
    }

    String emailKey(String email) {
        return db.queryForObject("SELECT lower(btrim(?) COLLATE \"C\")", String.class, email);
    }

    void notifyInvite(int userId, int actorId, UUID id, String message, Instant now) {
        db.update("""
                INSERT INTO notifications(user_id,actor_id,invitation_id,notification_type,message,read,created_at)
                VALUES (?,?,?,'PROJECT_INVITED',?,false,?)
                """, userId, actorId, id, message, java.time.LocalDateTime.ofInstant(now, java.time.ZoneId.systemDefault()));
    }

    /** Cùng ledger dọn quota của E1, với namespace riêng. Khóa và đếm hai bucket theo thứ tự actor rồi project/email. */
    int lockQuota(byte[] subject, Instant now) {
        OffsetDateTime window = time(now.truncatedTo(ChronoUnit.HOURS));
        db.update("""
                INSERT INTO verification_rate_limits(subject_hash,action,window_start,attempts)
                VALUES (?,'INVITE_SEND',?,0) ON CONFLICT DO NOTHING
                """, subject, window);
        return db.queryForObject("""
                SELECT attempts FROM verification_rate_limits WHERE subject_hash=? AND action='INVITE_SEND' AND window_start=? FOR UPDATE
                """, Integer.class, subject, window);
    }

    void countQuota(byte[] subject, Instant now) {
        db.update("""
                UPDATE verification_rate_limits SET attempts=attempts+1
                WHERE subject_hash=? AND action='INVITE_SEND' AND window_start=?
                """, subject, time(now.truncatedTo(ChronoUnit.HOURS)));
    }

    private static Instant instant(OffsetDateTime value) { return value.toInstant(); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
