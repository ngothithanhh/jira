package com.example.jira.dto.issue.issue_status;

import com.example.jira.enums.StatusCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueStatusResponse {
    private int statusId;
    private String statusName;
    private StatusCategory statusCategory;
}

