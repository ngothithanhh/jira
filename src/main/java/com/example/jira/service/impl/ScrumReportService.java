package com.example.jira.service.impl;

import com.example.jira.dto.report.ScrumReport.*;
import com.example.jira.entity.*;
import com.example.jira.enums.PermissionName;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class ScrumReportService {
    private final SprintRepository sprints;
    private final ProjectRepository projects;
    private final SprintMetricRepository metrics;
    private final SprintMetricPointRepository points;
    private final SprintMetricsService calculator;
    private final ProjectSecurity security;

    /** sprintId xác định project quyền đọc; current là dữ liệu hiện tại, commitment/completed lấy snapshot bất biến nếu có. */
    public Summary summary(int sprintId) {
        Sprint sprint = readableSprint(sprintId); SprintMetric metric = metrics.findBySprint_SprintId(sprintId).orElse(null);
        return new Summary(sprintId,sprint.getState(),calculator.totals(sprint),metric==null?null:metric.getCommittedPoints(),metric==null?null:metric.getCommittedCount(),
                metric==null?null:metric.getCompletedPoints(),metric==null?null:metric.getCompletedCount(),baseline(metric));
    }
    /** Trả event series đã lưu, không điền điểm giả trước lần quan sát; không ghi DB khi đọc report. */
    public Burndown burndown(int sprintId) {
        Sprint sprint = readableSprint(sprintId); SprintMetric metric = metrics.findBySprint_SprintId(sprintId).orElse(null);
        List<Point> history = metric==null?List.of():points.findByMetric_MetricIdOrderByRecordedAtAscPointIdAsc(metric.getMetricId()).stream()
                .map(point -> new Point(point.getRecordedAt(),point.getTotalPoints(),point.getRemainingPoints(),point.getIssueCount(),point.getRemainingCount(),point.getInvalidEstimateCount())).toList();
        return new Burndown(sprintId,sprint.getStartDate(),sprint.getEndDate(),metric==null?null:metric.getStartedAt(),metric==null?null:metric.getFinishedAt(),baseline(metric),history);
    }
    /** Lấy tối đa limit CLOSED trong đúng project; legacy không có snapshot trả null/available=false thay vì tính ngược từ issue hiện tại. */
    public List<Velocity> velocity(int projectId,int limit) {
        if (limit<1 || limit>50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"limit phải từ 1 đến 50");
        projects.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        security.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        List<Sprint> closed = sprints.findClosedForVelocity(projectId,PageRequest.of(0,limit));
        Map<Integer,SprintMetric> bySprint = new HashMap<>();
        if (!closed.isEmpty()) metrics.findBySprint_SprintIdIn(closed.stream().map(Sprint::getSprintId).toList()).forEach(metric -> bySprint.put(metric.getSprint().getSprintId(),metric));
        return closed.stream().map(sprint -> {
            SprintMetric metric = bySprint.get(sprint.getSprintId());
            return new Velocity(sprint.getSprintId(),sprint.getSprintName(),metric==null?null:metric.getCommittedPoints(),metric==null?null:metric.getCompletedPoints(),
                    metric==null?null:metric.getCompletedCount(),metric==null?null:metric.getFinishedAt(),metric!=null&&metric.getCompletedPoints()!=null,baseline(metric));
        }).toList();
    }
    /** Đọc sprint rồi kiểm VIEW_PROJECT theo chính project sở hữu, trước khi đọc snapshot/issue. */
    private Sprint readableSprint(int id) {
        Sprint sprint = sprints.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sprint"));
        security.requirePermission(sprint.getProject().getProjectId(),PermissionName.VIEW_PROJECT); return sprint;
    }
    /** Chỉ START_EVENT là commitment lúc bắt đầu; OBSERVED không được trình bày là lịch sử đầy đủ. */
    private boolean baseline(SprintMetric metric) { return metric!=null && "START_EVENT".equals(metric.getOrigin()); }
}
