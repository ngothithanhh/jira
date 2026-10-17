package com.example.jira.controller.project;

import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.RankBacklogIssueRequest;
import com.example.jira.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/backlog")
@RequiredArgsConstructor
public class ProductBacklogController {
    private final IssueService issueService;

    @GetMapping
    public ResponseEntity<List<IssueResponse>> getBacklog(@PathVariable int projectId) {
        return ResponseEntity.ok(issueService.getProductBacklog(projectId));
    }

    @PutMapping("/rank")
    public ResponseEntity<Void> rankIssue(@PathVariable int projectId,
                                          @Valid @RequestBody RankBacklogIssueRequest request) {
        issueService.rankBacklogIssue(projectId, request.getIssueId(), request.getBeforeIssueId());
        return ResponseEntity.noContent().build();
    }
}
