package com.example.jira.dto.issue.issue_type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectIssueTypeResponse {
    private int projectId;
    private int issueTypeId;
    private String issueTypeName;
    private boolean isDefault;
}
