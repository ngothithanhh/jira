package com.example.jira.dto.issue;

import com.example.jira.entity.WorkLog;
import java.time.LocalDateTime;

public record WorkLogResponse(int worklogId, int issueId, int userId, String userName,
                              int timeSpent, String comment, LocalDateTime loggedAt) {
    public static WorkLogResponse from(WorkLog log) {
        return new WorkLogResponse(log.getWorklogId(), log.getIssue().getIssueId(), log.getUser().getUserId(),
                log.getUser().getFullName(), log.getTimeSpent(), log.getComment(), log.getLoggedAt());
    }
}
