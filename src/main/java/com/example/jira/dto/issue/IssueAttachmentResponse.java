package com.example.jira.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueAttachmentResponse {
    private int attachmentId;
    private int issueId;
    private String filePath;
    private int uploadedByUserId;
    private String uploadedByUserName;
    private LocalDateTime uploadedAt;
}
