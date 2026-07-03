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

    @PostMapping
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssueStatusResponse> createStatus(@RequestBody @Valid IssueStatusRequest request) {
        return ResponseEntity.ok(issueStatusService.createStatus(request));
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssueStatusResponse> updateStatus(@PathVariable int id, @RequestBody @Valid IssueStatusRequest request) {
        return ResponseEntity.ok(issueStatusService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStatus(@PathVariable int id) {
        issueStatusService.deleteStatus(id);
        return ResponseEntity.noContent().build();
    }
}
