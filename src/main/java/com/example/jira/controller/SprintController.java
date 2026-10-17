package com.example.jira.controller;

import com.example.jira.dto.sprint.CreateSprintRequest;
import com.example.jira.dto.sprint.SprintResponse;
import com.example.jira.dto.sprint.UpdateSprintRequest;
import com.example.jira.dto.sprint.CompleteSprintRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping
    public ResponseEntity<SprintResponse> createSprint(@Valid @RequestBody CreateSprintRequest request) {
        return new ResponseEntity<>(sprintService.createSprint(request), HttpStatus.CREATED);
    }

    @GetMapping("/{sprintId}")
    public ResponseEntity<SprintResponse> getSprint(@PathVariable int sprintId) {
        return ResponseEntity.ok(sprintService.getSprint(sprintId));
    }

    @PutMapping("/{sprintId}")
    public ResponseEntity<SprintResponse> updateSprint(@PathVariable int sprintId,
                                                        @Valid @RequestBody UpdateSprintRequest request) {
        return ResponseEntity.ok(sprintService.updateSprint(sprintId, request));
    }

    @DeleteMapping("/{sprintId}")
    public ResponseEntity<Void> deleteSprint(@PathVariable int sprintId) {
        sprintService.deleteSprint(sprintId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<SprintResponse>> getSprintsByProject(@PathVariable int projectId) {
        return ResponseEntity.ok(sprintService.getSprintsByProject(projectId));
    }

    @GetMapping("/{sprintId}/backlog")
    public ResponseEntity<List<IssueResponse>> getSprintBacklog(@PathVariable int sprintId) {
        return ResponseEntity.ok(sprintService.getSprintBacklog(sprintId));
    }

    @PostMapping("/{sprintId}/issues/{issueId}")
    public ResponseEntity<Void> addIssueToSprint(@PathVariable int sprintId, @PathVariable int issueId) {
        sprintService.addIssueToSprint(sprintId, issueId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{sprintId}/issues/{issueId}")
    public ResponseEntity<Void> removeIssueFromSprint(@PathVariable int sprintId, @PathVariable int issueId) {
        sprintService.removeIssueFromSprint(sprintId, issueId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{sprintId}/start")
    public ResponseEntity<Void> startSprint(@PathVariable int sprintId) {
        sprintService.startSprint(sprintId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{sprintId}/complete")
    public ResponseEntity<Void> completeSprint(@PathVariable int sprintId,
                                               @RequestBody(required = false) CompleteSprintRequest request) {
        sprintService.completeSprint(sprintId, request == null ? null : request.getMoveToSprintId());
        return ResponseEntity.ok().build();
    }
}
