package com.example.jira.service.impl;

import com.example.jira.entity.AuditLog;
import com.example.jira.entity.User;
import com.example.jira.enums.EntityType;
import com.example.jira.repository.AuditLogRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.service.AuditLogService;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void logAction(String action, EntityType entityType, int entityId, String description) {
        // PROJECT dùng chính entityId; USER/ROLE toàn cục không được suy project từ actor.
        save(action, entityType, entityId, entityType == EntityType.PROJECT ? entityId : null, description);
    }

    /** Caller truyền project từ entity đã kiểm quyền trước khi xóa; ghi log cùng transaction nghiệp vụ, lỗi phải rollback. */
    @Override
    @Transactional
    public void logProjectAction(String action, EntityType entityType, int entityId, int projectId, String description) {
        if (projectId <= 0 || entityType == EntityType.USER || entityType == EntityType.ROLE
                || entityType == EntityType.PROJECT && projectId != entityId) {
            throw new IllegalArgumentException("Phạm vi project của audit không hợp lệ");
        }
        save(action, entityType, entityId, projectId, description);
    }

    /** Chỉ đọc danh tính từ SecurityContext rồi lưu audit_logs; không bắt lỗi DB thành log hệ thống. */
    private void save(String action, EntityType entityType, int entityId, Integer projectId, String description) {
        User user = null;
        // Chỉ tác vụ không có danh tính mới mang actor null; lỗi đọc user phải làm transaction thất bại.
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.example.jira.security.UserPrincipal) {
            int userId = SecurityUtils.getCurrentUserId();
            user = userRepository.findById(userId).orElse(null);
        }

        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setProjectId(projectId);
        log.setDescription(description);
        log.setCreatedAt(LocalDateTime.now());

        auditLogRepository.save(log);
    }
}
