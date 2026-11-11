package com.example.jira.dto.issue;
import jakarta.validation.constraints.*;
public record UpdateCommentRequest(@NotBlank @Size(max=10000) String commentText,@NotNull @PositiveOrZero Long expectedVersion) { }
