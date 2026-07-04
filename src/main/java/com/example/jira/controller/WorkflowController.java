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
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowRuleService workflowRuleService;

    @GetMapping("/rules")
    public ResponseEntity<List<WorkflowRuleResponse>> getAllRules() {
        return ResponseEntity.ok(workflowRuleService.getAllRules());
    }

    @PostMapping("/rules")
    public ResponseEntity<WorkflowRuleResponse> createRule(@RequestBody WorkflowRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowRuleService.createRule(request));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable int ruleId) {
        workflowRuleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/allowed-transitions")
    public ResponseEntity<List<IssueStatusResponse>> getAllowedNextStatuses(@RequestParam int currentStatusId) {
        return ResponseEntity.ok(workflowRuleService.getAllowedNextStatuses(currentStatusId));
    }
}
