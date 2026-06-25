package com.example.jira.controller.issue;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @PostMapping
    public ResponseEntity<IssueResponse> createIssue(@RequestBody CreateIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueService.createIssue(request));
    }

    @PutMapping("/{issueId}")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable int issueId,
            @RequestBody UpdateIssueRequest request) {
        return ResponseEntity.ok(issueService.updateIssue(issueId, request));
    }

    @PutMapping("/{issueId}/assign")
    public ResponseEntity<IssueResponse> assignIssue(
            @PathVariable int issueId,
            @RequestParam(required = false) Integer assigneeId) {
        return ResponseEntity.ok(issueService.assignIssue(issueId, assigneeId));
    }

    @PutMapping("/{issueId}/status")
    public ResponseEntity<IssueResponse> changeIssueStatus(
            @PathVariable int issueId,
            @RequestParam int statusId) {
        return ResponseEntity.ok(issueService.changeIssueStatus(issueId, statusId));
    }
}
