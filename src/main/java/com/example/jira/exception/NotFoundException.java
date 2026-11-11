package com.example.jira.exception;

public class NotFoundException extends RuntimeException {
    /** Dùng khi tra cứu ID không có bản ghi; MVC trả 404 và dừng thao tác hiện tại. */
    public NotFoundException(String message) {
        super(message);
    }
}
