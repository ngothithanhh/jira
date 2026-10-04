package com.example.jira.dto.issue.issue_type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueTypeResponse {
    private int issueTypeId;
    private String typeName;
}
