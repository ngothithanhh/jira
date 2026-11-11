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

    // ID user có thể vắng trên dữ liệu cũ; giữ null trong JSON, không thay bằng ID 0.
    private Integer reporterId;
    private String reporterName;
    // Issue chưa giao cho ai thì không có ID user nhận việc.
    private Integer assigneeId;
    private String assigneeName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Issue độc lập không có ID issue cha trong bảng issues.
    private Integer parentIssueId;
}
