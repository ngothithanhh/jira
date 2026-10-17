package com.example.jira.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        boolean success,
        String message,
        String code,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(String message, String code) {
        return ApiErrorResponse.builder().success(false).message(message).code(code).timestamp(Instant.now()).build();
    }

    public static ApiErrorResponse validation(Map<String, String> fieldErrors) {
        return ApiErrorResponse.builder().success(false).message("Validation failed").code("VALIDATION_ERROR")
                .timestamp(Instant.now()).fieldErrors(fieldErrors).build();
    }
}
