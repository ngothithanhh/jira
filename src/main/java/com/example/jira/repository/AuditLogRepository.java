package com.example.jira.repository;

import com.example.jira.entity.AuditLog;
import com.example.jira.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(EntityType entityType, int entityId);
}
