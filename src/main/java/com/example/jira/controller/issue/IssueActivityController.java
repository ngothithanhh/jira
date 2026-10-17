package com.example.jira.controller.issue;

import com.example.jira.enums.EntityType;
import com.example.jira.repository.AuditLogRepository;
import com.example.jira.service.IssueCollaborationAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/issues/{issueId}/activities")
@RequiredArgsConstructor
public class IssueActivityController {
    private final IssueCollaborationAccess access;
    private final AuditLogRepository audit;

    public record Activity(int id, String action, Integer userId, String userName, String description, LocalDateTime createdAt) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<Activity> list(@PathVariable int issueId) {
        access.requireView(issueId);
        return audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescAuditIdDesc(EntityType.ISSUE, issueId).stream()
                .map(a -> new Activity(a.getAuditId(), a.getAction(), a.getUser() == null ? null : a.getUser().getUserId(),
                        a.getUser() == null ? null : a.getUser().getFullName(), a.getDescription(), a.getCreatedAt())).toList();
    }
}
