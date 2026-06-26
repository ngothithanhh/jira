package com.example.jira.service;

import com.example.jira.enums.EntityType;

public interface AuditLogService {
    void logAction(String action, EntityType entityType, int entityId, String description);
}
