package com.example.jira.controller;

import com.example.jira.dto.audit.AdminData.AuditPage;
import com.example.jira.dto.audit.AuditSearchRequest;
import com.example.jira.service.impl.ProjectAuditQueryService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.WebDataBinder;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/audit-logs")
public class ProjectAuditController {
    private final ProjectAuditQueryService queries;

    // Spring có thể bind URI variables vào @ModelAttribute. Không đưa projectId của URL vào filter.
    @InitBinder
    void auditFilters(WebDataBinder binder) { binder.setDisallowedFields("projectId"); }

    /** Chỉ đọc: danh tính từ JWT, project từ path, query chỉ dùng lọc/phân trang. */
    @GetMapping
    public AuditPage audit(@PathVariable int projectId,@Valid @ModelAttribute AuditSearchRequest request, HttpServletRequest http) {
        if (projectId <= 0 || http.getParameterMap().containsKey("projectId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phạm vi audit phải lấy từ URL");
        }
        return queries.audit(projectId,request);
    }
}
