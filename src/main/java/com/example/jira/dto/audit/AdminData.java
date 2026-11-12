package com.example.jira.dto.audit;
import com.example.jira.enums.EntityType;
import java.time.LocalDateTime;
import java.util.List;
public final class AdminData {
    private AdminData() { }
    public record AuditEntry(int auditId,Integer actorId,String actorName,String action,EntityType entityType,Integer entityId,String description,LocalDateTime createdAt,Integer projectId) { }
    public record AuditPage(List<AuditEntry> items,int page,int size,long totalItems,int totalPages) { }
    public record Overview(long users,long enabledUsers,long enabledAdmins,long projects,long issues,long activeSprints,long closedSprints) { }
}
