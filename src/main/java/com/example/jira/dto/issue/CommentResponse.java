package com.example.jira.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentResponse {
    private int commentId;
    private int issueId;
    private int userId;
    private String userName;
    private String commentText;
    private LocalDateTime createdAt;
}
