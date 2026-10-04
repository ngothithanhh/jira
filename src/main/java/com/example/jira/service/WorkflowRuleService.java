package com.example.jira.service;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.dto.workflow.WorkflowRuleRequest;
import com.example.jira.dto.workflow.WorkflowRuleResponse;

import java.util.List;

public interface WorkflowRuleService {
    List<WorkflowRuleResponse> getAllRules(int projectId);
    WorkflowRuleResponse createRule(int projectId, WorkflowRuleRequest request);
    void deleteRule(int projectId, int ruleId);
    List<IssueStatusResponse> getAllowedNextStatuses(int projectId, int currentStatusId);
}
