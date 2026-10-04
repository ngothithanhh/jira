package com.example.jira.controller;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.dto.workflow.WorkflowRuleRequest;
import com.example.jira.dto.workflow.WorkflowRuleResponse;
import com.example.jira.service.WorkflowRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowRuleService workflowRuleService;

    @GetMapping("/rules")
    public ResponseEntity<List<WorkflowRuleResponse>> getAllRules(@PathVariable int projectId) {
        return ResponseEntity.ok(workflowRuleService.getAllRules(projectId));
    }

    @PostMapping("/rules")
    public ResponseEntity<WorkflowRuleResponse> createRule(@PathVariable int projectId, @RequestBody WorkflowRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowRuleService.createRule(projectId, request));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable int projectId, @PathVariable int ruleId) {
        workflowRuleService.deleteRule(projectId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/allowed-transitions")
    public ResponseEntity<List<IssueStatusResponse>> getAllowedNextStatuses(@PathVariable int projectId, @RequestParam int currentStatusId) {
        return ResponseEntity.ok(workflowRuleService.getAllowedNextStatuses(projectId, currentStatusId));
    }
}
