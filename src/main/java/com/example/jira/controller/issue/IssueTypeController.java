package com.example.jira.controller.issue;

import com.example.jira.dto.issue.issue_type.IssueTypeRequest;
import com.example.jira.dto.issue.issue_type.IssueTypeResponse;
import com.example.jira.service.IssueTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/issue-types")
@RequiredArgsConstructor
public class IssueTypeController {

    private final IssueTypeService issueTypeService;

    @GetMapping
    public ResponseEntity<List<IssueTypeResponse>> getAllIssueTypes() {
        return ResponseEntity.ok(issueTypeService.getAllIssueTypes());
    }

    @PostMapping
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssueTypeResponse> createIssueType(@RequestBody @Valid IssueTypeRequest request) {
        return ResponseEntity.ok(issueTypeService.createIssueType(request));
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssueTypeResponse> updateIssueType(@PathVariable int id, @RequestBody IssueTypeRequest request) {
        return ResponseEntity.ok(issueTypeService.updateIssueType(id, request));
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteIssueType(@PathVariable int id) {
        issueTypeService.deleteIssueType(id);
        return ResponseEntity.noContent().build();
    }
}

