package com.example.jira.dto.workflow;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRuleResponse {
    private int ruleId;
    private IssueStatusResponse fromStatus;
    private IssueStatusResponse toStatus;
}
