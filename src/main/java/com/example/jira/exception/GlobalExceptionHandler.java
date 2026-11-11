package com.example.jira.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    /** Hai lượt cùng ghi version cũ: trả 409 để client tải lại thay vì tự ghi đè dữ liệu mới. */
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ApiError> optimisticConflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Dữ liệu đã được cập nhật, vui lòng tải lại"));
    }
    /** Service từ chối quyền thì trả 403; không tiếp tục ghi dữ liệu hay trả stack trace. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN, exception.getMessage()));
    }

    /** Service không có danh tính hợp lệ thì trả cùng JSON 401 như JWT filter. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthorized(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc phiên đăng nhập không hợp lệ"));
    }

    /** ID do client gửi không còn tồn tại thì trả 404 với thông báo nghiệp vụ đã được service chọn. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    /** Xung đột nghiệp vụ đã xác định thì trả 409; không suy đoán loại lỗi từ chuỗi thông báo. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, exception.getMessage()));
    }

    /** DB vẫn có thể chặn trùng/FK sau bước kiểm tra; giấu nội dung SQL và trả 409 để client tải lại. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc đang được sử dụng"));
    }

    /** Giữ HTTP status do luồng cũ chỉ định, đồng thời thống nhất JSON cho lỗi SecurityUtils. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiError.of(status, exception.getReason()));
    }
}
