package com.example.jira.verification;

import com.example.jira.mail.MailMessage;
import com.example.jira.mail.MailOutbox;
import com.example.jira.security.UserPrincipal;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static com.example.jira.verification.VerificationStore.*;

@Service
@RequiredArgsConstructor
public class VerificationService {
    public record Registration(String email, String password, String userName, String fullName) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(UUID challengeId, String proofToken, String emailMasked, String state, Instant expiresAt,
                       Instant resendAvailableAt, Instant proofExpiresAt, String deliveryState, Instant verifiedAt) {
        @Override public String toString() { return "VerificationView[redacted]"; }
    }
    public record Result(HttpStatus status, String code, Long retryAfter, View view) { }

    private static final long OTP_SECONDS = 600;
    private static final long PROOF_SECONDS = 1800;
    private static final long COOLDOWN = 60;
    private static final int EMAIL_SEND_LIMIT = 5;
    private static final int EMAIL_VERIFY_LIMIT = 20;

    private final VerificationStore store;
    private final VerificationSecrets secrets;
    private final ObjectProvider<MailOutbox> outbox;
    private final PasswordEncoder encoder;
    @Value("${app.verification.ip-send-limit:30}") private int ipSendLimit;
    @Value("${app.verification.ip-verify-limit:120}") private int ipVerifyLimit;

    /** Giữ khóa hàng theo email chuẩn, thay cả hồ sơ và proof của lượt cũ rồi enqueue cùng transaction. */
    @Transactional
    public Result start(Registration request, String ip) {
        if (!valid(request)) return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        MailOutbox mail = outbox.getIfAvailable();
        if (!secrets.configured() || mail == null) return unavailable();
        Instant now = Instant.now();
        Result limited = chargeIp(ip, "SEND", ipSendLimit, now);
        if (limited != null) return limited;
        String email = canonical(request.email());
        if (store.emailExists(email)) return emailUsed();
        Challenge previous = store.reserveRegistration(email, now);
        if (store.emailExists(email)) return emailUsed();
        now = Instant.now();
        limited = chargeEmailSend(email, now);
        if (limited != null) return limited;
        var pending = new Pending(request.userName().trim(), request.fullName().trim(), encoder.encode(request.password()));
        return issue(Flow.REGISTRATION, null, mail, previous, email, email, pending, false, now);
    }

    /** Chỉ proof khớp ID đọc được generation đang chờ hoặc kết quả đã commit. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result registrationStatus(UUID id, String proof) {
        if (!secrets.configured()) return unavailable();
        Challenge current = id == null ? null : store.registration(id, false).orElse(null);
        Instant now = Instant.now();
        Result invalid = checkProof(current, proof, now);
        return invalid != null ? invalid : view(HttpStatus.OK, Flow.REGISTRATION, current, null, now);
    }

    /** Resend và verify cùng khóa registration_id. Chỉ resend thay generation và giữ proof hiện hành. */
    @Transactional
    public Result resend(UUID id, String proof, String ip) {
        if (!secrets.configured()) return unavailable();
        Result limited = chargeIp(ip, "SEND", ipSendLimit, Instant.now());
        if (limited != null) return limited;
        Challenge current = id == null ? null : store.registration(id, true).orElse(null);
        Instant now = Instant.now();
        Result invalid = checkProof(current, proof, now);
        if (invalid != null) return invalid;
        if (current.state() == State.CONSUMED || store.emailExists(current.email())) return emailUsed();
        if (current.state() == State.LOCKED) return limited("LOCKED", now, current.proofExpires());
        if (now.isBefore(current.resendAfter())) return limited("COOLDOWN", now, current.resendAfter());
        MailOutbox mail = outbox.getIfAvailable();
        if (mail == null) return unavailable();
        limited = chargeEmailSend(current.email(), now);
        if (limited != null) return limited;
        return issue(Flow.REGISTRATION, null, mail, current, current.email(), current.email(), current.pending(), true, now);
    }

    @Transactional
    public Result verifyRegistration(UUID id, String proof, String code, String ip) {
        if (!secrets.configured()) return unavailable();
        Result limited = chargeIp(ip, "VERIFY", ipVerifyLimit, Instant.now());
        if (limited != null) return limited;
        Challenge current = id == null ? null : store.registration(id, true).orElse(null);
        Instant now = Instant.now();
        Result invalid = checkProof(current, proof, now);
        if (invalid != null) return invalid;
        if (current.state() == State.CONSUMED) return done();
        invalid = checkOtp(Flow.REGISTRATION, current, code, now);
        if (invalid != null) return invalid;
        var created = store.createUser(current, now);
        if (created.isEmpty()) return emailUsed();
        int userId = created.orElseThrow();
        store.audit(null, userId, now);
        consume(Flow.REGISTRATION, current, userId, now);
        return done();
    }

    /** User ID và version lấy từ JWT. Khi đã xác minh, status không phụ thuộc cookie proof. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result accountStatus(UserPrincipal actor, UUID id, String proof) {
        Account account = account(actor, false);
        if (account == null) return unauthenticated();
        if (account.verifiedAt() != null) return accountView(account, "VERIFIED");
        if (id == null) return accountView(account, "UNVERIFIED");
        if (!secrets.configured()) return unavailable();
        Challenge current = accountChallenge(account, id, false);
        Instant now = Instant.now();
        Result invalid = checkProof(current, proof, now);
        return invalid != null ? invalid : view(HttpStatus.OK, Flow.ACCOUNT, current, null, now);
    }

    /** Khóa users trước challenge legacy, kiểm lại enabled/version/email rồi mới gửi tới email trong DB. */
    @Transactional
    public Result requestAccount(UserPrincipal actor, String ip) {
        Account observed = account(actor, false);
        if (observed == null) return unauthenticated();
        if (observed.verifiedAt() != null) return accountView(observed, "VERIFIED");
        if (!secrets.configured()) return unavailable();
        Result limited = chargeIp(ip, "SEND", ipSendLimit, Instant.now());
        if (limited != null) return limited;
        Account account = account(actor, true);
        if (account == null) return unauthenticated();
        if (account.verifiedAt() != null) return accountView(account, "VERIFIED");
        MailOutbox mail = outbox.getIfAvailable();
        if (mail == null) return unavailable();
        String email = canonical(account.email());
        if (!validEmail(account.email().trim())) return error(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        Challenge current = store.accountChallenge(account.id(), true).orElse(null);
        Instant now = Instant.now();
        boolean active = current != null && now.isBefore(current.proofExpires()) && email.equals(current.email());
        if (active && current.state() == State.LOCKED) return limited("LOCKED", now, current.proofExpires());
        if (active && now.isBefore(current.resendAfter())) return limited("COOLDOWN", now, current.resendAfter());
        limited = chargeEmailSend(email, now);
        if (limited != null) return limited;
        return issue(Flow.ACCOUNT, account.id(), mail, current, email, account.email().trim(), null, false, now);
    }

    @Transactional
    public Result verifyAccount(UserPrincipal actor, UUID id, String proof, String code, String ip) {
        if (!secrets.configured()) return unavailable();
        Result limited = chargeIp(ip, "VERIFY", ipVerifyLimit, Instant.now());
        if (limited != null) return limited;
        Account account = account(actor, true);
        if (account == null) return unauthenticated();
        Challenge current = accountChallenge(account, id, true);
        Instant now = Instant.now();
        Result invalid = checkProof(current, proof, now);
        if (invalid != null) return invalid;
        if (account.verifiedAt() != null && current.state() == State.CONSUMED) return done();
        invalid = checkOtp(Flow.ACCOUNT, current, code, now);
        if (invalid != null) return invalid;
        store.verifyAccount(account.id(), now);
        store.audit(account.id(), account.id(), now);
        consume(Flow.ACCOUNT, current, account.id(), now);
        return done();
    }

    private Account account(UserPrincipal actor, boolean lock) {
        return actor == null ? null : store.account(actor.getId(), lock)
                .filter(user -> user.enabled() && user.authVersion() == actor.getAuthVersion()
                        && user.email().equals(actor.getUsername())).orElse(null);
    }

    private Challenge accountChallenge(Account account, UUID id, boolean lock) {
        return store.accountChallenge(account.id(), lock)
                .filter(row -> row.id().equals(id) && row.email().equals(canonical(account.email()))).orElse(null);
    }

    private Result issue(Flow flow, Integer userId, MailOutbox mail, Challenge previous, String email, String recipient,
                         Pending pending, boolean resend, Instant now) {
        UUID id = resend ? previous.id() : UUID.randomUUID();
        String proof = resend ? null : secrets.proof();
        byte[] hash = resend ? previous.proof() : secrets.proofHash(proof);
        int generation = previous == null ? 1 : Math.incrementExact(previous.generation());
        Instant proofExpires = resend ? previous.proofExpires() : now.plusSeconds(PROOF_SECONDS);
        Instant expires = now.plusSeconds(OTP_SECONDS);
        if (proofExpires.isBefore(expires)) expires = proofExpires;
        byte[] history = resend ? previous.history() : new byte[0];
        String code;
        byte[] historyHash;
        do {
            code = secrets.otp();
            historyHash = secrets.otpHistoryHash(id, code);
        } while (containsHash(history, historyHash) || (previous != null && previous.otp() != null
                && MessageDigest.isEqual(previous.otp(), secrets.otpHash(previous.id(), previous.generation(), code))));
        if (flow == Flow.REGISTRATION) {
            int used = history.length;
            history = Arrays.copyOf(history, used + historyHash.length);
            System.arraycopy(historyHash, 0, history, used, historyHash.length);
        }
        var next = new Challenge(id, email, hash, generation, secrets.otpHash(id, generation, code), history, expires,
                proofExpires, now.plusSeconds(COOLDOWN), 0, State.PENDING, pending);
        if (previous != null) store.supersede(flow, previous);
        if (flow == Flow.REGISTRATION) store.saveRegistration(next, now);
        else store.saveAccount(userId, next, now);
        mail.enqueue(flow.mailKind, id, generation, new MailMessage(recipient, "Jira - Mã xác minh email",
                "Mã xác minh Jira của bạn: " + code + ". Mã có hiệu lực tối đa 10 phút."), expires)
                .orElseThrow(() -> new IllegalStateException("Không tạo được job xác minh email"));
        return new Result(HttpStatus.ACCEPTED, null, null,
                new View(id, proof, mask(email), "PENDING", expires, next.resendAfter(), proofExpires, "QUEUED", null));
    }

    private static boolean containsHash(byte[] history, byte[] candidate) {
        for (int offset = 0; offset < history.length; offset += candidate.length) {
            if (MessageDigest.isEqual(candidate, Arrays.copyOfRange(history, offset, offset + candidate.length))) return true;
        }
        return false;
    }

    private Result checkProof(Challenge row, String proof, Instant now) {
        if (row == null || !secrets.proofMatches(row.proof(), proof)) return error(HttpStatus.BAD_REQUEST, "INVALID_PROOF");
        if (!now.isBefore(row.proofExpires())) return error(HttpStatus.GONE, "PROOF_EXPIRED");
        return null;
    }

    /** Trả lỗi dưới dạng dữ liệu để PostgreSQL commit attempt và state LOCKED trước khi controller trả HTTP. */
    private Result checkOtp(Flow flow, Challenge row, String code, Instant now) {
        if (row.state() == State.LOCKED) return limited("LOCKED", now, row.proofExpires());
        if (row.state() != State.PENDING || !now.isBefore(row.expires())) return error(HttpStatus.GONE, "OTP_EXPIRED");
        if (code == null || !code.matches("[0-9]{6}")) return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        Rate rate = rate("email", row.email(), "VERIFY", now);
        if (store.lockRate(rate) >= EMAIL_VERIFY_LIMIT) return limited("RATE_LIMIT", now, rate.window().plusSeconds(3600));
        if (MessageDigest.isEqual(row.otp(), secrets.otpHash(row.id(), row.generation(), code))) return null;
        store.incrementRate(rate);
        store.failedAttempt(flow, row, now);
        if (row.attempts() + 1 == 5) {
            store.supersede(flow, row);
            return limited("LOCKED", now, row.proofExpires());
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_CODE");
    }

    private void consume(Flow flow, Challenge row, int userId, Instant now) {
        store.finish(flow, row.id(), State.CONSUMED, userId, row.attempts(), now);
        store.supersede(flow, row);
    }

    private Result view(HttpStatus status, Flow flow, Challenge row, String proof, Instant now) {
        String state = row.state() == State.PENDING && !now.isBefore(row.expires()) ? "EXPIRED" : row.state().name();
        String delivery = store.deliveryState(flow, row);
        return new Result(status, null, null, new View(row.id(), proof, mask(row.email()), state, row.expires(),
                row.resendAfter(), row.proofExpires(), delivery, null));
    }

    private Result chargeIp(String ip, String action, int limit, Instant now) {
        return charge(rate("ip", Objects.requireNonNullElse(ip, "unavailable"), action, now), limit, now);
    }

    private Result chargeEmailSend(String email, Instant now) {
        return charge(rate("email", email, "SEND", now), EMAIL_SEND_LIMIT, now);
    }

    private Rate rate(String type, String identity, String action, Instant now) {
        return new Rate(secrets.subject(type, identity), action, now.truncatedTo(ChronoUnit.HOURS));
    }

    private Result charge(Rate rate, int limit, Instant now) {
        if (store.lockRate(rate) >= limit) return limited("RATE_LIMIT", now, rate.window().plusSeconds(3600));
        store.incrementRate(rate);
        return null;
    }

    private static Result accountView(Account account, String state) {
        return new Result(HttpStatus.OK, null, null,
                new View(null, null, mask(canonical(account.email())), state, null, null, null, null, account.verifiedAt()));
    }

    static Result error(HttpStatus status, String code) { return new Result(status, code, null, null); }
    private static Result done() { return new Result(HttpStatus.NO_CONTENT, null, null, null); }
    private static Result unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE"); }
    private static Result unauthenticated() { return error(HttpStatus.UNAUTHORIZED, "SESSION_CHANGED"); }
    private static Result emailUsed() { return error(HttpStatus.CONFLICT, "EMAIL_IN_USE"); }
    private static Result limited(String code, Instant now, Instant until) {
        return new Result(HttpStatus.TOO_MANY_REQUESTS, code, Math.max(1, until.getEpochSecond() - now.getEpochSecond() + 1), null);
    }

    static String canonical(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, email.offsetByCodePoints(0, 1)) + "***" + email.substring(at) : "***";
    }

    private static boolean valid(Registration request) {
        return request != null && request.email() != null && request.email().trim().matches("[!-~]+")
                && validEmail(canonical(request.email()))
                && field(request.userName(), 50) && field(request.fullName(), 100)
                && request.password() != null && !request.password().isBlank() && request.password().length() >= 8
                && request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static boolean field(String value, int length) {
        return value != null && !value.isBlank() && value.length() <= length && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validEmail(String email) {
        if (email.length() > 100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) return false;
        try {
            new MailMessage(email, "Jira", "Xác minh email");
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
