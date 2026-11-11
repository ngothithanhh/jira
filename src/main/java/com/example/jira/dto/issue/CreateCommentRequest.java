package com.example.jira.dto.issue;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {
    @NotBlank(message = "Nội dung bình luận không được để trống")
    @jakarta.validation.constraints.Size(max=10000)
    private String commentText;
}
