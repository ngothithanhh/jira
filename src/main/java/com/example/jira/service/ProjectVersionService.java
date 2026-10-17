package com.example.jira.service;

import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.mapper.IssueMapper;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectVersionService {
    private final ProjectVersionRepository versions;
    private final IssueVersionRepository links;
    private final ProjectRepository projects;
    private final IssueRepository issues;
    private final ProjectSecurity security;
    private final AuditLogService audit;
    public record Request(@NotBlank @Size(max=100) String versionName, boolean released,
                          LocalDateTime releaseDate, @Size(max=255) String description) {}
    public record Response(int versionId, int projectId, String versionName, boolean released,
                           LocalDateTime releaseDate, String description) {}
    private Response response(ProjectVersion v) {
        return new Response(v.getVersionId(), v.getProject().getProjectId(), v.getVersionName(), v.isReleased(), v.getReleaseDate(), v.getDescription());
    }
    private void check(int projectId, boolean edit) {
        if (!security.hasPermission(projectId, edit ? PermissionName.UPDATE_PROJECT : PermissionName.VIEW_PROJECT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project permission denied");
    }
    private ProjectVersion load(int id, boolean edit) {
        ProjectVersion v = versions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        check(v.getProject().getProjectId(), edit); return v;
    }
    @Transactional(readOnly=true)
    public List<Response> list(int projectId) { check(projectId, false); return versions.findByProject_ProjectIdOrderByVersionIdDesc(projectId).stream().map(this::response).toList(); }
    @Transactional(readOnly=true)
    public Response get(int id) { return response(load(id, false)); }
    public Response save(int projectId, Integer id, Request request) {
        check(projectId, true);
        ProjectVersion v = id == null ? new ProjectVersion() : load(id, true);
        if (id != null && v.getProject().getProjectId() != projectId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        v.setProject(projects.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        v.setVersionName(request.versionName().trim()); v.setReleased(request.released());
        v.setReleaseDate(request.releaseDate()); v.setDescription(request.description());
        versions.save(v);
        audit.logAction(id == null ? "VERSION_CREATED" : "VERSION_UPDATED", EntityType.PROJECT_VERSION, v.getVersionId(), v.getVersionName());
        return response(v);
    }
    public void delete(int id) {
        ProjectVersion v = load(id, true); links.deleteByProjectVersion_VersionId(id); versions.delete(v);
        audit.logAction("VERSION_DELETED", EntityType.PROJECT_VERSION, id, v.getVersionName());
    }
    public void attach(int id, int issueId, boolean remove) {
        ProjectVersion v = load(id, true);
        Issue issue = issues.findByIssueIdAndDeletedAtIsNull(issueId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (issue.getProject().getProjectId() != v.getProject().getProjectId()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue must belong to version project");
        IssueVersionId key = new IssueVersionId(issueId, id);
        if (remove) links.deleteById(key);
        else if (!links.existsById(key)) { IssueVersion link = new IssueVersion(); link.setId(key); link.setIssue(issue); link.setProjectVersion(v); links.save(link); }
        audit.logAction(remove ? "VERSION_ISSUE_REMOVED" : "VERSION_ISSUE_ADDED", EntityType.PROJECT_VERSION, id, "Issue " + issueId);
    }
    @Transactional(readOnly=true)
    public List<IssueResponse> issues(int id) {
        load(id, false); return links.findByProjectVersion_VersionId(id).stream().map(IssueVersion::getIssue)
            .filter(i -> i.getDeletedAt() == null).map(IssueMapper::toResponse).toList();
    }
}
