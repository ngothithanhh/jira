package com.example.jira.invitations;

import com.example.jira.entity.Project;
import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.ProjectMemberId;
import com.example.jira.entity.User;
import com.example.jira.entity.UserRole;
import com.example.jira.enums.EntityType;
import com.example.jira.enums.PermissionName;
import com.example.jira.exception.NotFoundException;
import com.example.jira.mail.MailMessage;
import com.example.jira.mail.MailOutbox;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.security.UserPrincipal;
import com.example.jira.service.AuditLogService;
import com.example.jira.verification.VerificationSecrets;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.example.jira.invitations.InvitationStore.*;

@Service
@RequiredArgsConstructor
public class ProjectInvitationService {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(UUID invitationId, Integer projectId, String projectName, String projectKey, String inviterName,
                       String email, String roleName, State state, Instant expiresAt, Instant resendAfter, String deliveryState,
                       String deliveryError, boolean canResend) { }
    public record Page(List<View> items, boolean hasMore) { }
    public record Result(int status, String code, Long retryAfter, Object value) { }
    public static final class Rejected extends RuntimeException {
        final Result result;
        Rejected(int status, String code) { this(new Result(status, code, null, null)); }
        Rejected(Result result) { super(result.code()); this.result = result; }
    }

    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration MAX_LIFETIME = Duration.ofDays(14);
    private static final int COOLDOWN = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final MailOutbox.Kind KIND = MailOutbox.Kind.PROJECT_INVITATION;
    private final InvitationStore store;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final ProjectMemberRepository members;
    private final ProjectSecurity security;
    private final EntityManager entityManager;
    private final AuditLogService audit;
    private final ObjectProvider<MailOutbox> outbox;
    private final VerificationSecrets secrets;
    @Value("${app.public-base-url:${APP_PUBLIC_BASE_URL:}}") private String publicBase;

    /** Hai đường create cùng giữ khóa project và qua một nghiệp vụ. Compatibility không ghi membership. */
    @Transactional
    public View create(int projectId, UserPrincipal actor, String email, Integer legacyUser, int roleId) {
        Project project = lockProject(projectId);
        refreshMembership(projectId, actor);
        requireSender(projectId, actor);
        User target = legacyUser == null ? null : users.findById(legacyUser)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người nhận"));
        String canonical = canonicalEmail(target == null ? email : target.getEmail());
        if (target == null) target = users.findByCanonicalEmail(canonical).orElse(null);
        if (target != null && (!store.activeUser(target.getUserId()) ||
                members.existsById(new ProjectMemberId(projectId, target.getUserId())))) {
            throw new Rejected(409, "RECIPIENT_UNAVAILABLE");
        }
        UserRole role = role(roleId, projectId);
        Instant now = Instant.now();
        var previous = store.pending(projectId, canonical).orElse(null);
        if (previous != null) {
            if (previous.currentState(now) == State.PENDING) throw new Rejected(409, "ALREADY_PENDING");
            decide(previous, State.EXPIRED, null, now);
        }
        MailOutbox mail = mail();
        charge(actor.getId(), projectId, canonical, now);
        UUID id = UUID.randomUUID();
        String token = token();
        Instant expires = now.plus(TTL);
        store.insert(id, projectId, actor.getId(), target == null ? null : target.getUserId(), canonical, role.getRoleId(),
                digest(token), now, expires, now.plusSeconds(COOLDOWN));
        enqueue(mail, id, 1, token, target == null ? canonical : target.getEmail().trim(), project.getProjectName(), expires);
        log("INVITE_CREATED", projectId, id);
        if (target != null) store.notifyInvite(target.getUserId(), actor.getId(), id, "Bạn có lời mời tham gia dự án.", now);
        return view(find(projectId, id, false), true, now);
    }

    @Transactional(readOnly = true)
    public Page projectList(int projectId, UserPrincipal actor, State state, int offset, int size) {
        requireSender(projectId, actor);
        Instant now = Instant.now();
        return page(store.forProject(projectId, state, now, offset, size + 1), size, true, now);
    }

    @Transactional
    public Result resend(int projectId, UUID id, UserPrincipal actor) {
        lockProject(projectId);
        refreshMembership(projectId, actor);
        requireSender(projectId, actor);
        var invite = find(projectId, id, true);
        Instant now = Instant.now();
        Result stop = requirePending(invite, now);
        if (stop != null) return stop;
        if (invite.generation() >= 4) throw new Rejected(409, "RESEND_LIMIT");
        if (now.isBefore(invite.resendAfter())) throw new Rejected(limited("COOLDOWN", now, invite.resendAfter()));
        var target = users.findByCanonicalEmail(invite.email()).orElse(null);
        if (target != null && (!store.activeUser(target.getUserId()) ||
                members.existsById(new ProjectMemberId(projectId, target.getUserId())))) {
            throw new Rejected(409, "RECIPIENT_UNAVAILABLE");
        }
        MailOutbox mail = mail();
        charge(actor.getId(), projectId, invite.email(), now);
        String token = token();
        Instant maximum = invite.createdAt().plus(MAX_LIFETIME);
        Instant expires = now.plus(TTL).isBefore(maximum) ? now.plus(TTL) : maximum;
        mail.supersedeThrough(KIND, id, invite.generation());
        store.rotate(invite, digest(token), expires, now.plusSeconds(COOLDOWN), now);
        enqueue(mail, id, invite.generation() + 1, token, target == null ? invite.email() : target.getEmail().trim(),
                invite.projectName(), expires);
        log("INVITE_RESENT", projectId, id);
        return success(202, view(find(projectId, id, false), true, now));
    }

    @Transactional
    public Result revoke(int projectId, UUID id, UserPrincipal actor) {
        lockProject(projectId);
        refreshMembership(projectId, actor);
        requireSender(projectId, actor);
        var invite = find(projectId, id, true);
        if (invite.state() == State.REVOKED) return success(204, null);
        Instant now = Instant.now();
        Result stop = requirePending(invite, now);
        if (stop != null) return stop;
        decide(invite, State.REVOKED, null, now);
        return success(204, null);
    }

    @Transactional(readOnly = true)
    public Page inbox(UserPrincipal actor, int offset, int size) {
        User user = current(actor, false);
        return page(store.forRecipient(store.emailKey(user.getEmail()), user.getUserId(), offset, size + 1),
                size, false, Instant.now());
    }

    @Transactional(readOnly = true)
    public View preview(UUID id, UserPrincipal actor, boolean link, String token) {
        User user = current(actor, false);
        var invite = find(projectId(id), id, false);
        checkRecipient(invite, user);
        checkProof(invite, link, token);
        return view(invite, false, Instant.now());
    }

    /** Thứ tự khóa cố định: projects, users của recipient, rồi lời mời. Không khóa user inviter. */
    @Transactional
    public Result respond(UUID id, UserPrincipal actor, boolean link, String token, boolean accept) {
        int projectId = projectId(id);
        Project project = lockProject(projectId);
        User user = current(actor, true);
        var invite = find(projectId, id, true);
        checkRecipient(invite, user);
        checkProof(invite, link, token);
        if (user.getEmailVerifiedAt() == null) throw new Rejected(403, "EMAIL_VERIFICATION_REQUIRED");
        if (accept && invite.state() == State.ACCEPTED) return success(200, view(invite, false, Instant.now()));
        if (!accept && invite.state() == State.DECLINED) return success(204, null);
        Instant now = Instant.now();
        Result stop = requirePending(invite, now);
        if (stop != null) return stop;
        if (!accept) {
            decide(invite, State.DECLINED, null, now);
            return success(204, null);
        }
        Integer inviter = invite.inviterId();
        if (inviter != null) refreshMembership(projectId, inviter);
        if (inviter == null || !store.activeUser(inviter)
                || !security.hasPermissionForUser(projectId, PermissionName.ADD_MEMBER, inviter)) {
            decide(invite, State.REVOKED, null, now);
            return error(410, "INVITER_NO_LONGER_AUTHORIZED");
        }
        ProjectMemberId memberId = new ProjectMemberId(projectId, user.getUserId());
        if (members.existsById(memberId)) throw new Rejected(409, "ALREADY_MEMBER");
        UserRole role = role(invite.roleId(), projectId);
        ProjectMember member = new ProjectMember();
        member.setId(memberId);
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        member.setAssignedAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
        entityManager.persist(member);
        entityManager.flush();
        decide(invite, State.ACCEPTED, user.getUserId(), now);
        store.notifyInvite(inviter, user.getUserId(), null, "Lời mời tham gia " + project.getProjectName() + " đã được chấp nhận.", now);
        entityManager.flush();
        return success(200, view(find(projectId, id, false), false, now));
    }

    private Result requirePending(Invitation invite, Instant now) {
        State state = invite.currentState(now);
        if (state == State.PENDING) return null;
        if (invite.state() == State.PENDING) decide(invite, State.EXPIRED, null, now);
        return state == State.ACCEPTED ? error(409, "STATE_CONFLICT") : error(410, state.name());
    }

    private void decide(Invitation invite, State state, Integer acceptedUser, Instant now) {
        store.decide(invite, state, acceptedUser, now);
        log("INVITE_" + state.name(), invite.projectId(), invite.id());
    }

    private void checkRecipient(Invitation invite, User user) {
        if (!invite.email().equals(store.emailKey(user.getEmail()))
                || (invite.state() == State.ACCEPTED && !Integer.valueOf(user.getUserId()).equals(invite.acceptedUserId()))) {
            throw new Rejected(403, "WRONG_ACCOUNT");
        }
    }

    private void checkProof(Invitation invite, boolean link, String token) {
        if (link && (token == null || !token.matches("[A-Za-z0-9_-]{43}")
                || !MessageDigest.isEqual(invite.tokenHash(), digest(token)))) throw new Rejected(410, "LINK_INVALID");
    }

    private Project lockProject(int id) {
        Project project = projects.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy dự án"));
        entityManager.refresh(project);
        return project;
    }

    private User current(UserPrincipal actor, boolean lock) {
        if (actor == null) throw new Rejected(401, "SESSION_CHANGED");
        var user = (lock ? users.findByIdForUpdate(actor.getId()) : users.findById(actor.getId()))
                .orElseThrow(() -> new Rejected(401, "SESSION_CHANGED"));
        if (lock) entityManager.refresh(user);
        if (!user.isEnabled() || user.getAuthVersion() != actor.getAuthVersion()) throw new Rejected(401, "SESSION_CHANGED");
        return user;
    }

    private void refreshMembership(int projectId, UserPrincipal actor) {
        if (actor != null) refreshMembership(projectId, actor.getId());
    }

    private void refreshMembership(int projectId, int userId) {
        members.findByProject_ProjectIdAndUser_UserId(projectId, userId).ifPresent(entityManager::refresh);
    }

    private void requireSender(int projectId, UserPrincipal actor) {
        current(actor, false);
        if (!security.hasPermission(projectId, PermissionName.ADD_MEMBER)) throw new AccessDeniedException("Bạn không có quyền mời thành viên");
    }

    private UserRole role(int roleId, int projectId) {
        UserRole role = roles.findByRoleId(roleId).orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role.getRoleName())) throw new Rejected(409, "ROLE_NOT_ALLOWED");
        if ("PROJECT_MANAGER".equalsIgnoreCase(role.getRoleName()) && members.countOtherProjectManagers(projectId, 0) > 0) {
            throw new Rejected(409, "MANAGER_EXISTS");
        }
        return role;
    }

    private int projectId(UUID id) {
        return store.projectId(id).orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời"));
    }

    private Invitation find(int projectId, UUID id, boolean lock) {
        return store.find(projectId, id, lock).orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời"));
    }

    private MailOutbox mail() {
        MailOutbox mail = outbox.getIfAvailable();
        if (mail == null) throw new Rejected(503, "MAIL_UNAVAILABLE");
        return mail;
    }

    private void enqueue(MailOutbox mail, UUID id, int generation, String token, String email, String projectName, Instant expires) {
        String link = baseUrl() + "/app/invitations/" + id + "#token=" + token;
        mail.enqueue(KIND, id, generation, new MailMessage(email, "Jira - Lời mời tham gia dự án",
                "Bạn được mời tham gia dự án " + projectName + ". Mở lời mời:\n" + link), expires)
                .orElseThrow(() -> new IllegalStateException("Không tạo được thư mời"));
    }

    private String baseUrl() {
        try {
            URI uri = URI.create(publicBase);
            String host = uri.getHost();
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "[::1]".equals(host);
            if (host != null && uri.getUserInfo() == null && uri.getFragment() == null && uri.getQuery() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && ("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme())))) {
                return publicBase.replaceFirst("/$", "");
            }
        } catch (IllegalArgumentException ignored) { }
        throw new Rejected(503, "MAIL_UNAVAILABLE");
    }

    private void charge(int actorId, int projectId, String email, Instant now) {
        byte[] actor = secrets.invitationQuotaSubject("actor:" + actorId);
        byte[] recipient = secrets.invitationQuotaSubject("recipient:" + projectId + ":" + email);
        if (actor == null || recipient == null) throw new Rejected(503, "MAIL_UNAVAILABLE");
        if (store.lockQuota(actor, now) >= 20 || store.lockQuota(recipient, now) >= 5) {
            throw new Rejected(limited("RATE_LIMIT", now, now.truncatedTo(ChronoUnit.HOURS).plusSeconds(3600)));
        }
        store.countQuota(actor, now);
        store.countQuota(recipient, now);
    }

    private void log(String action, int projectId, UUID id) {
        audit.logAction(action, EntityType.PROJECT, projectId, "Lời mời " + id);
    }

    private static Page page(List<Invitation> rows, int size, boolean sender, Instant now) {
        return new Page(rows.stream().limit(size).map(row -> view(row, sender, now)).toList(), rows.size() > size);
    }

    private static View view(Invitation row, boolean sender, Instant now) {
        State state = row.currentState(now);
        return new View(row.id(), sender || state == State.ACCEPTED ? row.projectId() : null, row.projectName(),
                row.projectKey(), row.inviterName(), sender ? row.email() : null, row.roleName(), state, row.expiresAt(),
                sender ? row.resendAfter() : null, sender ? row.deliveryState() : null, sender ? row.deliveryError() : null,
                sender && state == State.PENDING && row.generation() < 4);
    }

    private static String canonicalEmail(String email) {
        if (email == null || email.trim().length() > 100 || !email.trim().matches("[!-~]+")
                || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new Rejected(400, "INVALID_INPUT");
        try { return new MailMessage(emailKey(email), "Jira", "Lời mời").recipient(); }
        catch (IllegalArgumentException invalid) { throw new Rejected(400, "INVALID_INPUT"); }
    }

    private static String emailKey(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] digest(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Result success(int status, Object value) { return new Result(status, null, null, value); }
    private static Result error(int status, String code) { return new Result(status, code, null, null); }
    private static Result limited(String code, Instant now, Instant until) {
        return new Result(429, code, Math.max(1, until.getEpochSecond() - now.getEpochSecond() + 1), null);
    }
}
