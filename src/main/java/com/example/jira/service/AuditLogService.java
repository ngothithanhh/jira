package com.example.jira.service;

import com.example.jira.enums.EntityType;

public interface AuditLogService {
    /** Ghi thao tác con với ID projects đã được caller kiểm quyền, kể cả entity vừa bị xóa. */
    void logProjectAction(String action, EntityType entityType, int entityId, int projectId, String description);
    void logAction(String action, EntityType entityType, int entityId, String description);
}
