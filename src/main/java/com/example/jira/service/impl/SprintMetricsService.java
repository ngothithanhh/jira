package com.example.jira.service.impl;

import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.dto.report.ScrumReport.Totals;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class SprintMetricsService {
    private final SprintRepository sprints;
    private final SprintIssueRepository links;
    private final SprintMetricRepository metrics;
    private final SprintMetricPointRepository points;

    /** Caller vừa đặt ACTIVE và giữ khóa project; lưu commitment/điểm đầu cùng transaction start, không ghi đè snapshot cũ. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void start(Sprint sprint) {
        if (metrics.findBySprint_SprintId(sprint.getSprintId()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sprint đã có snapshot bắt đầu");
        Totals totals = totals(sprint); SprintMetric metric = create(sprint,"START_EVENT",totals);
        record(metric,totals,true);
    }

    /**
     * Caller vừa ghi membership/status/estimate/delete và giữ khóa project trong transaction.
     * Chỉ snapshot các sprint ACTIVE của projectId, ghi điểm khi tổng/remaining/count thay đổi.
     * ACTIVE cũ chưa snapshot dùng OBSERVED tại thời điểm hiện tại, không giả định biết commitment lúc bắt đầu.
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public void captureActiveProject(int projectId) {
        for (Sprint sprint : sprints.findByProject_ProjectIdAndState(projectId,SprintState.ACTIVE)) {
            Totals totals = totals(sprint);
            var existing = metrics.findBySprint_SprintId(sprint.getSprintId());
            SprintMetric metric = existing.orElseGet(() -> create(sprint,"OBSERVED",totals));
            record(metric,totals,existing.isEmpty());
        }
    }

    /** Caller đã kiểm mọi điều kiện complete, chưa chuyển việc chưa DONE; chốt số hoàn thành trước khi membership thay đổi. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void finish(Sprint sprint) {
        Totals totals = totals(sprint);
        SprintMetric metric = metrics.findBySprint_SprintId(sprint.getSprintId()).orElseGet(() -> create(sprint,"OBSERVED",totals));
        if (metric.getFinishedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sprint đã có snapshot kết thúc");
        metric.setCompletedPoints(totals.totalPoints()-totals.remainingPoints()); metric.setCompletedCount(totals.issueCount()-totals.remainingCount());
        metric.setFinishedAt(LocalDateTime.now()); metrics.save(metric);
    }

    /** Caller đã chuyển/gỡ việc và đặt CLOSED; lưu điểm cuối phản ánh scope còn lại, không đổi completed snapshot. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void captureClosed(Sprint sprint) {
        SprintMetric metric = metrics.findBySprint_SprintId(sprint.getSprintId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Thiếu snapshot kết thúc"));
        record(metric,totals(sprint),true);
    }

    /** Chỉ đọc liên kết cùng project; DONE theo category, estimate âm legacy tính 0 và trả số bản ghi lỗi để report công khai. */
    @Transactional(readOnly=true)
    public Totals totals(Sprint sprint) {
        long total=0,remaining=0,count=0,remainingCount=0,invalid=0;
        for (SprintIssue link : links.findBacklog(sprint.getSprintId())) {
            Issue issue = link.getIssue();
            if (issue.getProject() == null || issue.getProject().getProjectId() != sprint.getProject().getProjectId()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Sprint có issue khác dự án");
            long estimate = Math.max(0,issue.getStoryPoints()); if (issue.getStoryPoints() < 0) invalid++;
            total += estimate; count++;
            if (issue.getStatus() == null || issue.getStatus().getStatusCategory() != StatusCategory.DONE) { remaining += estimate; remainingCount++; }
        }
        return new Totals(total,remaining,count,remainingCount,invalid);
    }

    /** Ghi một snapshot mới với baseline có nguồn rõ ràng; ID metric do DB cấp, không phải sprintId. */
    private SprintMetric create(Sprint sprint,String origin,Totals totals) {
        var metric = new SprintMetric(); metric.setSprint(sprint); metric.setOrigin(origin); metric.setStartedAt(LocalDateTime.now());
        metric.setCommittedPoints(totals.totalPoints()); metric.setCommittedCount(totals.issueCount());
        return metrics.saveAndFlush(metric);
    }

    /** Ghi event khi số liệu đổi hoặc start/finish bắt buộc; caller giữ khóa project, lỗi rollback cùng mutation gốc. */
    private void record(SprintMetric metric,Totals totals,boolean force) {
        if (!force && metric.getLastTotal()==totals.totalPoints() && metric.getLastRemaining()==totals.remainingPoints()
                && metric.getLastCount()==totals.issueCount() && metric.getLastRemainingCount()==totals.remainingCount() && metric.getLastInvalidCount()==totals.invalidEstimateCount()) return;
        var point = new SprintMetricPoint(); point.setMetric(metric); point.setRecordedAt(LocalDateTime.now());
        point.setTotalPoints(totals.totalPoints()); point.setRemainingPoints(totals.remainingPoints()); point.setIssueCount(totals.issueCount());
        point.setRemainingCount(totals.remainingCount()); point.setInvalidEstimateCount(totals.invalidEstimateCount()); points.save(point);
        metric.setLastTotal(totals.totalPoints()); metric.setLastRemaining(totals.remainingPoints()); metric.setLastCount(totals.issueCount());
        metric.setLastRemainingCount(totals.remainingCount()); metric.setLastInvalidCount(totals.invalidEstimateCount()); metrics.save(metric);
    }
}
