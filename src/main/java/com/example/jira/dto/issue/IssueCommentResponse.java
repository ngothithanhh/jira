package com.example.jira.dto.issue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCommentResponse {
    private int commentId;
    private String content;
    
    private int userId;
    private String userName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
