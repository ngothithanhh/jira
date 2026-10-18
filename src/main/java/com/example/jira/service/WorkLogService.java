package com.example.jira.service;

import com.example.jira.dto.issue.*;
import com.example.jira.entity.*;
import com.example.jira.enums.EntityType;
import com.example.jira.repository.*;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkLogService {
    private final WorkLogRepository logs;
    private final UserRepository users;
    private final IssueCollaborationAccess access;
    private final AuditLogService audit;

    public WorkLogResponse create(int issueId, WorkLogRequest request) {
        Issue issue = access.requireView(issueId);
        WorkLog log = new WorkLog();
        log.setIssue(issue);
        log.setUser(users.findById(SecurityUtils.getCurrentUserId()).orElseThrow());
        apply(log, request);
        logs.save(log);
        audit.logAction("WORKLOG_CREATED", EntityType.ISSUE, issueId, "Logged " + log.getTimeSpent() + " minutes; worklog " + log.getWorklogId());
        return WorkLogResponse.from(log);
    }

    public WorkLogResponse update(int issueId, int id, WorkLogRequest request) {
        Issue issue = access.requireView(issueId);
        WorkLog log = find(issueId, id);
        access.requireOwnerOrEditor(issue, log.getUser().getUserId());
        String description = "Worklog " + id + ": " + log.getTimeSpent() + " -> " + request.timeSpent() + " minutes";
        apply(log, request);
        audit.logAction("WORKLOG_UPDATED", EntityType.ISSUE, issueId, description);
        return WorkLogResponse.from(logs.save(log));
    }

    public void delete(int issueId, int id) {
        Issue issue = access.requireView(issueId);
        WorkLog log = find(issueId, id);
        access.requireOwnerOrEditor(issue, log.getUser().getUserId());
        audit.logAction("WORKLOG_DELETED", EntityType.ISSUE, issueId, "Deleted worklog " + id + ": " + log.getTimeSpent() + " minutes");
        logs.delete(log);
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> list(int issueId) {
        access.requireView(issueId);
        return logs.findByIssue_IssueIdOrderByLoggedAtDescWorklogIdDesc(issueId).stream().map(WorkLogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long total(int issueId) {
        access.requireView(issueId);
        return logs.totalTimeSpent(issueId);
    }

    private WorkLog find(int issueId, int id) {
        return logs.findByWorklogIdAndIssue_IssueId(id, issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi thời gian làm việc"));
    }
    private void apply(WorkLog log, WorkLogRequest request) {
        if (request.timeSpent() < 1 || request.loggedAt() == null || request.loggedAt().isAfter(java.time.LocalDateTime.now())
                || (request.comment() != null && request.comment().length() > 255))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin ghi nhận thời gian làm việc không hợp lệ");
        log.setTimeSpent(request.timeSpent());
        log.setComment(request.comment());
        log.setLoggedAt(request.loggedAt());
    }
}
