package com.example.jira.exception;

import com.example.jira.controller.WorkflowController;
import com.example.jira.controller.issue.IssueStatusController;
import com.example.jira.controller.issue.IssueTypeController;
import com.example.jira.controller.project.ProjectIssueTypeController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Trả 400 ngay tại controller cấu hình để lỗi nhập liệu không đi qua tuyến /error yêu cầu phiên khác. */
@RestControllerAdvice(assignableTypes = {IssueTypeController.class, IssueStatusController.class,
        ProjectIssueTypeController.class, WorkflowController.class})
public class ConfigurationInputAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidInput() {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "Dữ liệu cấu hình không hợp lệ"));
    }
}
