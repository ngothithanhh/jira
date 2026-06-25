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
public class IssueResponse {
    private int issueId;
    private String issueKey; // VD: PROJ-123
    private String summary;
    private String description;
    private String priority;
    private int storyPoints;
    
    private String statusName;
    private String issueTypeName;

    private int projectId;
    private String projectKey;

    private int reporterId;
    private String reporterName;
    private int assigneeId;
    private String assigneeName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private int parentIssueId;
}
