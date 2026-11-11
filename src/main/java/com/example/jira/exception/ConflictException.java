package com.example.jira.exception;

public class ConflictException extends RuntimeException {
    /** Dùng khi dữ liệu trùng hoặc còn được tham chiếu; MVC trả 409 thay vì lỗi máy chủ. */
    public ConflictException(String message) {
        super(message);
    }
}
