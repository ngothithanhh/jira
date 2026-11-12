package com.example.jira.verification;

import com.example.jira.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VerificationController {
    private static final String ID_HEADER = "X-Jira-Challenge-Id";
    private static final String PROOF_HEADER = "X-Jira-Verification-Proof";
    private final VerificationService service;

    /** Register chỉ chấp nhận hồ sơ do chính người dùng nhập. Không đọc quyền hoặc trạng thái user từ body. */
    @PostMapping("/api/auth/register")
    public ResponseEntity<?> start(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!fields(body, "email", "password", "userName", "fullName")) return invalid();
        return respond(service.start(new VerificationService.Registration(text(body, "email"), text(body, "password"),
                text(body, "userName"), text(body, "fullName")), request.getRemoteAddr()));
    }

    @GetMapping("/api/auth/registration/status")
    public ResponseEntity<?> registrationStatus(@RequestHeader(value = ID_HEADER, required = false) UUID id,
                                               @RequestHeader(value = PROOF_HEADER, required = false) String proof) {
        return respond(service.registrationStatus(id, proof));
    }

    @PostMapping("/api/auth/registration/resend")
    public ResponseEntity<?> resend(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!fields(body, "challengeId", "proofToken")) return invalid();
        return respond(service.resend(id(body), text(body, "proofToken"), request.getRemoteAddr()));
    }

    @PostMapping("/api/auth/registration/verify")
    public ResponseEntity<?> verifyRegistration(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!fields(body, "challengeId", "proofToken", "code") || !validCode(body)) return invalid();
        return respond(service.verifyRegistration(id(body), text(body, "proofToken"), text(body, "code"), request.getRemoteAddr()));
    }

    @GetMapping("/api/account/email-verification/status")
    public ResponseEntity<?> accountStatus(@AuthenticationPrincipal UserPrincipal actor,
                                           @RequestHeader(value = ID_HEADER, required = false) UUID id,
                                           @RequestHeader(value = PROOF_HEADER, required = false) String proof) {
        return respond(service.accountStatus(actor, id, proof));
    }

    @PostMapping("/api/account/email-verification/request")
    public ResponseEntity<?> requestAccount(@AuthenticationPrincipal UserPrincipal actor,
                                            @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        if (body != null && (!body.isObject() || !body.isEmpty())) return invalid();
        return respond(service.requestAccount(actor, request.getRemoteAddr()));
    }

    @PostMapping("/api/account/email-verification/verify")
    public ResponseEntity<?> verifyAccount(@AuthenticationPrincipal UserPrincipal actor,
                                           @RequestBody JsonNode body, HttpServletRequest request) {
        if (!fields(body, "challengeId", "proofToken", "code") || !validCode(body)) return invalid();
        return respond(service.verifyAccount(actor, id(body), text(body, "proofToken"), text(body, "code"), request.getRemoteAddr()));
    }

    private static boolean fields(JsonNode body, String... keys) {
        if (body == null || !body.isObject() || body.size() != keys.length) return false;
        for (String key : keys) if (!body.path(key).isTextual()) return false;
        return true;
    }

    private static String text(JsonNode body, String key) { return body.path(key).textValue(); }

    private static boolean validCode(JsonNode body) { return text(body, "code").matches("[0-9]{6}"); }

    private static UUID id(JsonNode body) {
        String value = text(body, "challengeId");
        try { return value.length() == 36 ? UUID.fromString(value) : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static ResponseEntity<?> invalid() {
        return respond(VerificationService.error(HttpStatus.BAD_REQUEST, "INVALID_INPUT"));
    }

    /** Lỗi ghi đã rollback. Giữ response 500 có nội dung cố định, không chuyển sang error dispatch đòi JWT. */
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    ResponseEntity<?> failedTransaction() {
        return respond(VerificationService.error(HttpStatus.INTERNAL_SERVER_ERROR, "VERIFICATION_UNCERTAIN"));
    }

    /** Chuyển kết quả sang HTTP sau khi service đã commit counter hoặc giao dịch nghiệp vụ. */
    private static ResponseEntity<?> respond(VerificationService.Result result) {
        var response = ResponseEntity.status(result.status()).header(HttpHeaders.CACHE_CONTROL, "no-store, private");
        if (result.retryAfter() != null) response.header(HttpHeaders.RETRY_AFTER, result.retryAfter().toString());
        if (result.code() == null) return result.view() == null ? response.build() : response.body(result.view());
        String message = switch (result.code()) {
            case "EMAIL_IN_USE" -> "Email đã được sử dụng. Vui lòng đăng nhập.";
            case "INVALID_CODE" -> "Mã xác minh không đúng.";
            case "INVALID_PROOF" -> "Lượt xác minh không còn hợp lệ. Vui lòng bắt đầu lại.";
            case "OTP_EXPIRED" -> "Mã xác minh đã hết hạn.";
            case "PROOF_EXPIRED" -> "Lượt xác minh đã hết hạn. Vui lòng bắt đầu lại.";
            case "COOLDOWN" -> "Chưa đến thời điểm gửi lại mã.";
            case "LOCKED" -> "Đã nhập sai quá số lần cho phép.";
            case "RATE_LIMIT" -> "Đã vượt giới hạn xác minh. Vui lòng thử lại sau.";
            case "MAIL_UNAVAILABLE" -> "Chưa thể gửi mã xác minh. Vui lòng thử lại sau.";
            case "VERIFICATION_UNCERTAIN" -> "Chưa xác nhận được kết quả. Vui lòng kiểm tra trạng thái.";
            case "SESSION_CHANGED" -> "Phiên đăng nhập đã thay đổi.";
            case "INVALID_EMAIL" -> "Email của tài khoản không hợp lệ.";
            default -> "Thông tin xác minh không hợp lệ.";
        };
        return response.body(Map.of("code", result.code(), "message", message));
    }
}
