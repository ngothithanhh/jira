package com.example.jira.controller;

import com.example.jira.dto.audit.AdminData.AuditPage;
import com.example.jira.dto.audit.AuditSearchRequest;
import com.example.jira.service.impl.ProjectAuditQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/audit-logs")
public class ProjectAuditController {
    private final ProjectAuditQueryService queries;

    /** Chỉ đọc: danh tính từ JWT, project từ path, query chỉ dùng lọc/phân trang. */
    @GetMapping
    public AuditPage audit(@PathVariable int projectId,@Valid @ModelAttribute AuditSearchRequest request) {
        return queries.audit(projectId,request);
    }
}
