package com.example.jira.dto.issue.issue_type;

import lombok.Data;

@Data
public class ProjectIssueTypeRequest {
    private int projectId;
    private int issueTypeId;
    private boolean isDefault;
}
