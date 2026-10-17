package com.example.jira.controller.issue;

import com.example.jira.dto.issue.*;
import com.example.jira.service.WorkLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issues/{issueId}/worklogs")
@RequiredArgsConstructor
public class WorkLogController {
    private final WorkLogService service;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkLogResponse create(@PathVariable int issueId,
                                  @Valid @RequestBody WorkLogRequest request) {
        return service.create(issueId, request);
    }
    @GetMapping
    public List<WorkLogResponse> list(@PathVariable int issueId) {
        return service.list(issueId);
    }
    @GetMapping("/total")
    public Map<String, Long> total(@PathVariable int issueId) {
        return Map.of("totalMinutes", service.total(issueId));
    }
    @PutMapping("/{id}")
    public WorkLogResponse update(@PathVariable int issueId,
                                  @PathVariable int id,
                                  @Valid @RequestBody WorkLogRequest request) {
        return service.update(issueId, id, request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int issueId,
                       @PathVariable int id) {
        service.delete(issueId, id);
    }
}
