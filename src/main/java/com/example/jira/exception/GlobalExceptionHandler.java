package com.example.jira.exception;

import com.example.jira.dto.common.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(org.springframework.security.core.AuthenticationException exception) {
        if (exception instanceof org.springframework.security.authentication.LockedException) {
            return error(HttpStatus.UNAUTHORIZED, "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.", "ACCOUNT_LOCKED");
        }
        if (exception instanceof org.springframework.security.authentication.DisabledException) {
            return error(HttpStatus.UNAUTHORIZED, "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.", "ACCOUNT_DISABLED");
        }
        if (exception instanceof org.springframework.security.authentication.BadCredentialsException
                || exception instanceof org.springframework.security.core.userdetails.UsernameNotFoundException) {
            return error(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng.", "INVALID_CREDENTIALS");
        }
        return error(HttpStatus.UNAUTHORIZED, "Không thể xác thực tài khoản. Vui lòng đăng nhập lại.", "UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.", "FORBIDDEN");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(fieldErrors));
    }

    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại.", "BAD_REQUEST"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Thao tác yêu cầu không được hỗ trợ", "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng dữ liệu gửi lên không được hỗ trợ", "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return error(HttpStatus.NOT_ACCEPTABLE, "Định dạng phản hồi được yêu cầu không được hỗ trợ", "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp tải lên vượt quá dung lượng cho phép.", "PAYLOAD_TOO_LARGE");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String fallback = "Không thể thực hiện yêu cầu. Vui lòng thử lại.";
        return error(status, safeMessage(exception.getReason(), fallback), codeFor(status.value()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of("Không tìm thấy dữ liệu được yêu cầu.", "NOT_FOUND"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of("Dữ liệu đã tồn tại hoặc đang được sử dụng. Vui lòng kiểm tra lại.", "CONFLICT"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException exception) {
        String message = safeMessage(exception.getMessage(), "Không thể xử lý yêu cầu. Vui lòng kiểm tra dữ liệu và thử lại.");
        String normalized = message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "quyền", "permission", "unauthorized", "not have permission")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(message, "PROJECT_PERMISSION_DENIED"));
        }
        if (containsAny(normalized, "not found", "không tìm thấy")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(message, "NOT_FOUND"));
        }
        if (containsAny(normalized, "đã tồn tại", "đã có", "already", "duplicate", "reuse", "tái sử dụng")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(message, "CONFLICT"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiErrorResponse.of(message, "BUSINESS_RULE_VIOLATION"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of("Hệ thống đang gặp lỗi. Vui lòng thử lại sau.", "INTERNAL_ERROR"));
    }

    private static String safeMessage(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatusCode status, String message, String code) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(message, code));
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "BUSINESS_RULE_VIOLATION";
            default -> "REQUEST_FAILED";
        };
    }
}
