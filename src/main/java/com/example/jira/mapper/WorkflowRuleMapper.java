package com.example.jira.mapper;

import com.example.jira.dto.workflow.WorkflowRuleResponse;
import com.example.jira.entity.WorkflowRule;

public class WorkflowRuleMapper {

    public static WorkflowRuleResponse toResponse(WorkflowRule rule) {
        if (rule == null) {
            return null;
        }

        WorkflowRuleResponse response = new WorkflowRuleResponse();
        response.setRuleId(rule.getRuleId());
        
        if (rule.getFromStatus() != null) {
            response.setFromStatus(IssueStatusMapper.toResponse(rule.getFromStatus()));
        }
        
        if (rule.getToStatus() != null) {
            response.setToStatus(IssueStatusMapper.toResponse(rule.getToStatus()));
        }

        return response;
    }
}
