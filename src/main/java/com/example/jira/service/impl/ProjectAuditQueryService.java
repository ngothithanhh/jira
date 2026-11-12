package com.example.jira.service.impl;

import com.example.jira.dto.audit.AdminData.AuditPage;
import com.example.jira.dto.audit.AuditSearchRequest;
import com.example.jira.enums.PermissionName;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.security.ProjectSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class ProjectAuditQueryService {
    private final ProjectRepository projects;
    private final ProjectSecurity security;
    private final AdminQueryService queries;

    /** projectId lấy từ URL projects; chỉ lead/SYS hoặc thành viên được cấp UPDATE_PROJECT đọc log của đúng project này. */
    @Transactional(readOnly=true)
    public AuditPage audit(int projectId, AuditSearchRequest request) {
        // Kiểm quyền trước nội dung; VIEW_PROJECT không đủ để đọc lịch sử quản lý.
        security.requirePermission(projectId,PermissionName.UPDATE_PROJECT);
        if (!projects.existsById(projectId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy dự án");
        return queries.projectAudit(projectId,request);
    }
}
