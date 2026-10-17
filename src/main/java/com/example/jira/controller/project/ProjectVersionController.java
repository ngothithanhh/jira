package com.example.jira.controller.project;
import com.example.jira.service.ProjectVersionService;
import com.example.jira.service.ProjectVersionService.*;
import com.example.jira.dto.issue.IssueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProjectVersionController {
    private final ProjectVersionService service;
    @GetMapping("/projects/{projectId}/versions")
    public List<Response> list(@PathVariable int projectId) {
        return service.list(projectId);
    }
    @PostMapping("/projects/{projectId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable int projectId, @Valid @RequestBody Request request) {
        return service.save(projectId, null, request);
    }
    @GetMapping("/versions/{id}")
    public Response get(@PathVariable int id) {
        return service.get(id);
    }
    @PutMapping("/projects/{projectId}/versions/{id}")
    public Response update(@PathVariable int projectId,
                           @PathVariable int id,
                           @Valid @RequestBody Request request) {
        return service.save(projectId, id, request);
    }
    @DeleteMapping("/versions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        service.delete(id);
    }
    @GetMapping("/versions/{id}/issues")
    public List<IssueResponse> issues(@PathVariable int id) {
        return service.issues(id);
    }
    @PutMapping("/versions/{id}/issues/{issueId}")
    public void add(@PathVariable int id, @PathVariable int issueId) {
        service.attach(id, issueId, false);
    }
    @DeleteMapping("/versions/{id}/issues/{issueId}")
    public void remove(@PathVariable int id, @PathVariable int issueId) {
        service.attach(id, issueId, true);
    }
}
