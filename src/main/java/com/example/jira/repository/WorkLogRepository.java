package com.example.jira.repository;

import com.example.jira.entity.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface WorkLogRepository extends JpaRepository<WorkLog, Integer> {
    List<WorkLog> findByIssue_IssueIdOrderByLoggedAtDescWorklogIdDesc(int issueId);
    Optional<WorkLog> findByWorklogIdAndIssue_IssueId(int worklogId, int issueId);
    @Query("select coalesce(sum(w.timeSpent), 0) from WorkLog w where w.issue.issueId = :issueId")
    long totalTimeSpent(int issueId);
}
