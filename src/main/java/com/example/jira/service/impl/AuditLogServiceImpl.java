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
        int userId = -1;
        User user = null;
        try {
            userId = SecurityUtils.getCurrentUserId();
            user = userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            // Không có context user (system action)
        }

        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setCreatedAt(LocalDateTime.now());

        auditLogRepository.save(log);
    }
}
