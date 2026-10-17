package com.example.jira.service;

import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ScrumReportService {
    private final SprintRepository sprints;
    private final SprintIssueRepository memberships;
    private final IssueRepository issues;
    private final IssueTransitionRepository transitions;
    private final ProjectSecurity security;
    public record Summary(int sprintId, String sprintName, int totalIssues, int doneIssues, int notDoneIssues, int completedStoryPoints) {}
    public record BurnPoint(LocalDate date, int remainingStoryPoints, int totalStoryPoints) {}
    public record Row(int issueId, String issueKey, String summary, Integer assigneeId, Integer statusId, int storyPoints) {}
    private void check(int projectId) {
        if (!security.hasPermission(projectId, PermissionName.VIEW_PROJECT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project permission denied");
    }
    private Sprint sprint(int id) {
        Sprint s = sprints.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        check(s.getProject().getProjectId()); return s;
    }
    private boolean done(Issue i) { return i.getStatus() != null && i.getStatus().getStatusCategory() == StatusCategory.DONE; }
    public Summary summary(int id) {
        Sprint s = sprint(id); List<SprintIssue> rows = memberships.findBySprint_SprintId(id);
        int count = 0, points = 0;
        for (SprintIssue row : rows) {
            boolean completed = row.getCompleted() != null ? row.getCompleted() : done(row.getIssue());
            if (completed) { count++; points += row.getFinalStoryPoints() != null ? row.getFinalStoryPoints() : row.getIssue().getStoryPoints(); }
        }
        return new Summary(id, s.getSprintName(), rows.size(), count, rows.size()-count, points);
    }
    public List<Summary> velocity(int projectId) {
        check(projectId); return sprints.findByProject_ProjectId(projectId).stream().filter(s -> s.getState() == SprintState.CLOSED)
            .sorted(Comparator.comparing(Sprint::getEndDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(Sprint::getSprintId))
            .map(s -> summary(s.getSprintId())).toList();
    }
    public List<BurnPoint> burndown(int id) {
        Sprint s = sprint(id);
        if (s.getStartDate() == null || s.getEndDate() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Sprint requires start and end dates");
        LocalDate end = s.getEndDate().toLocalDate();
        if (s.getCompletedAt() != null && s.getCompletedAt().toLocalDate().isBefore(end)) end = s.getCompletedAt().toLocalDate();
        if (LocalDate.now().isBefore(end)) end = LocalDate.now();
        if (java.time.temporal.ChronoUnit.DAYS.between(s.getStartDate().toLocalDate(), end) > 3660) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint date range too large");
        List<SprintIssue> rows = memberships.findBySprint_SprintId(id);
        Map<Integer,List<IssueTransition>> history = new HashMap<>();
        for (SprintIssue row : rows) history.put(row.getIssue().getIssueId(), transitions.findByIssue_IssueIdOrderByTransitionedAtDesc(row.getIssue().getIssueId()));
        List<BurnPoint> result = new ArrayList<>();
        for (LocalDate day = s.getStartDate().toLocalDate(); !day.isAfter(end); day = day.plusDays(1)) {
            LocalDateTime cutoff = day.plusDays(1).atStartOfDay();
            if (s.getCompletedAt() != null && s.getCompletedAt().isBefore(cutoff)) cutoff = s.getCompletedAt();
            int total=0, remaining=0;
            for (SprintIssue row : rows) {
                if (row.getAddedAt() != null && !row.getAddedAt().isBefore(cutoff)) continue;
                Issue i = row.getIssue();
                int points = row.getFinalStoryPoints() == null ? i.getStoryPoints() : row.getFinalStoryPoints();
                IssueStatus status = i.getStatus();
                for (IssueTransition transition : history.get(i.getIssueId())) {
                    if (transition.getTransitionedAt() != null && !transition.getTransitionedAt().isBefore(cutoff)) status = transition.getFromStatus();
                }
                boolean completed = status != null && status.getStatusCategory() == StatusCategory.DONE;
                if (s.getCompletedAt() != null && cutoff.equals(s.getCompletedAt()) && row.getCompleted() != null) completed = row.getCompleted();
                total += points; if (!completed) remaining += points;
            }
            result.add(new BurnPoint(day, remaining, total));
        }
        return result;
    }
    public List<Row> issues(int projectId, Integer sprintId, Integer assigneeId, Integer statusId) {
        check(projectId);
        List<Issue> selected;
        if (sprintId != null) {
            Sprint s = sprint(sprintId);
            if (s.getProject().getProjectId() != projectId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint belongs to another project");
            selected = memberships.findBySprint_SprintId(sprintId).stream().map(SprintIssue::getIssue).toList();
        } else selected = issues.findByProject_ProjectIdAndDeletedAtIsNull(projectId);
        return selected.stream().filter(i -> i.getDeletedAt() == null)
            .filter(i -> assigneeId == null || i.getAssignee() != null && i.getAssignee().getUserId() == assigneeId)
            .filter(i -> statusId == null || i.getStatus() != null && i.getStatus().getStatusId() == statusId)
            .sorted(Comparator.comparingInt(Issue::getIssueId))
            .map(i -> new Row(i.getIssueId(), i.getIssueKey(), i.getSummary(), i.getAssignee() == null ? null : i.getAssignee().getUserId(), i.getStatus() == null ? null : i.getStatus().getStatusId(), i.getStoryPoints())).toList();
    }
    public static String csvCell(String value) {
        if (value == null) return "\"\"";
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
