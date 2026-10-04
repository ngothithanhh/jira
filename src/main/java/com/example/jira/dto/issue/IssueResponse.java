package com.example.jira.dto.issue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private Integer storyPoints;
    private LocalDate dueDate;
    private Long backlogRank;
    private Integer boardPosition;
    
    private Integer statusId;
    private String statusName;
    private Integer issueTypeId;
    private String issueTypeName;

    private Integer projectId;
    private String projectKey;

    private Integer reporterId;
    private String reporterName;
    private Integer assigneeId;
    private String assigneeName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer parentIssueId;
}
