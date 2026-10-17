package com.example.jira.controller.issue;

import com.example.jira.dto.issue.issue_status.IssueStatusRequest;
import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.service.IssueStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/issue-statuses")
@RequiredArgsConstructor
public class IssueStatusController {

    private final IssueStatusService issueStatusService;

    @GetMapping
    public ResponseEntity<List<IssueStatusResponse>> getAllStatuses() {
        return ResponseEntity.ok(issueStatusService.getAllStatuses());
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping
    public ResponseEntity<IssueStatusResponse> createStatus(@RequestBody @Valid IssueStatusRequest request) {
        return ResponseEntity.ok(issueStatusService.createStatus(request));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<IssueStatusResponse> updateStatus(@PathVariable int id, @RequestBody @Valid IssueStatusRequest request) {
        return ResponseEntity.ok(issueStatusService.updateStatus(id, request));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStatus(@PathVariable int id) {
        issueStatusService.deleteStatus(id);
        return ResponseEntity.noContent().build();
    }
}
