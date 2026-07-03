package com.example.jira.controller.project;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.service.ProjectIssueTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/issue-types")
@RequiredArgsConstructor
public class ProjectIssueTypeController {

    private final ProjectIssueTypeService projectIssueTypeService;

    @GetMapping
    public ResponseEntity<List<ProjectIssueTypeResponse>> getIssueTypesForProject(@PathVariable int projectId) {
        return ResponseEntity.ok(projectIssueTypeService.getProjectIssueTypes(projectId));
    }

    @PostMapping("/{issueTypeId}")
//    @PreAuthorize("hasPermission(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<Void> assignIssueTypeToProject(
            @PathVariable int projectId,
            @PathVariable int issueTypeId,
            @RequestParam(defaultValue = "false") boolean isDefault) {

        projectIssueTypeService.assignIssueType(projectId, issueTypeId, isDefault);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{issueTypeId}/default")
//    @PreAuthorize("hasPermission(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<Void> setDefaultIssueType(
            @PathVariable int projectId,
            @PathVariable int issueTypeId) {

        projectIssueTypeService.setDefaultIssueType(projectId, issueTypeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{issueTypeId}")
//    @PreAuthorize("hasPermission(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<Void> removeIssueTypeFromProject(
            @PathVariable int projectId,
            @PathVariable int issueTypeId) {

        projectIssueTypeService.removeIssueType(projectId, issueTypeId);
        return ResponseEntity.noContent().build();
    }
}

