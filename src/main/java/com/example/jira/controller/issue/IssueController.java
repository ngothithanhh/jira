package com.example.jira.controller.issue;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.dto.issue.IssueTransitionResponse;
import com.example.jira.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

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

    @GetMapping("/project/{projectId}")
    public ResponseEntity<java.util.List<IssueResponse>> getIssuesByProject(
            @PathVariable int projectId) {
        return ResponseEntity.ok(issueService.getIssuesByProject(projectId));
    }

    @GetMapping
    public ResponseEntity<Page<IssueResponse>> searchIssues(
            @RequestParam int projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer assigneeId,
            @RequestParam(required = false) Integer issueTypeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return ResponseEntity.ok(issueService.searchIssues(projectId, q, statusId, assigneeId, issueTypeId, page, size, sort));
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<IssueResponse> getIssue(@PathVariable int issueId) {
        return ResponseEntity.ok(issueService.getIssue(issueId));
    }

    @GetMapping("/{issueId}/transitions")
    public ResponseEntity<java.util.List<IssueTransitionResponse>> getIssueTransitions(@PathVariable int issueId) {
        return ResponseEntity.ok(issueService.getIssueTransitions(issueId));
    }

    @DeleteMapping("/{issueId}")
    public ResponseEntity<Void> deleteIssue(@PathVariable int issueId) {
        issueService.deleteIssue(issueId);
        return ResponseEntity.noContent().build();
    }
}
