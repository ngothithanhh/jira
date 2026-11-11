package com.example.jira.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record ApiError(int status, String error, String message) {
    /** Tạo cùng cấu trúc JSON cho lỗi ở MVC và lỗi xác thực trước khi request tới controller. */
    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(status.value(), status.getReasonPhrase(), message);
    }

    /** Ghi lỗi từ filter; chỉ trả thông báo cố định, không đưa token hoặc lỗi parser ra client. */
    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        new ObjectMapper().writeValue(response.getWriter(), of(status, message));
    }
}
