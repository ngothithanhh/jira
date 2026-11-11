package com.example.jira.regression;

import com.example.jira.dto.report.ScrumReport.Totals;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScrumReportRegressionTest {
    @Nested @ExtendWith(MockitoExtension.class)
    class Metrics {
        @Mock SprintRepository sprints; @Mock SprintIssueRepository links; @Mock SprintMetricRepository metrics; @Mock SprintMetricPointRepository points;
        @InjectMocks SprintMetricsService service;
        Sprint sprint = Fixtures.sprint(100,10,SprintState.ACTIVE);
        private SprintIssue link(Issue issue) { var link = new SprintIssue(); link.setSprint(sprint); link.setIssue(issue); return link; }
        @Test void totalsUseCategoryAndLongAndExposeInvalidEstimates() {
            var a = Fixtures.issue(5,10); a.setStoryPoints(Integer.MAX_VALUE);
            var b = Fixtures.issue(6,10); b.setStoryPoints(Integer.MAX_VALUE); b.setStatus(Fixtures.status(3,"Completed",StatusCategory.DONE));
            var c = Fixtures.issue(7,10); c.setStoryPoints(-1);
            when(links.findBacklog(100)).thenReturn(List.of(link(a),link(b),link(c)));
            Totals totals = service.totals(sprint); assertThat(totals.totalPoints()).isEqualTo(4294967294L);
            assertThat(totals.remainingPoints()).isEqualTo(Integer.MAX_VALUE); assertThat(totals.invalidEstimateCount()).isEqualTo(1); assertThat(totals.remainingCount()).isEqualTo(2);
        }
        @Test void crossProjectDataStopsBeforeSnapshotWrite() {
            when(links.findBacklog(100)).thenReturn(List.of(link(Fixtures.issue(5,20))));
            assertThatThrownBy(() -> service.start(sprint)).isInstanceOf(ResponseStatusException.class); verify(metrics,never()).saveAndFlush(any()); verifyNoInteractions(points);
        }
        @Test void startSavesCommitmentAndFirstPoint() {
            var issue = Fixtures.issue(5,10); issue.setStoryPoints(5); when(links.findBacklog(100)).thenReturn(List.of(link(issue)));
            when(metrics.saveAndFlush(any())).thenAnswer(call -> { SprintMetric metric=call.getArgument(0); metric.setMetricId(7L); return metric; });
            service.start(sprint);
            verify(metrics).saveAndFlush(argThat(metric -> metric.getCommittedPoints()==5 && "START_EVENT".equals(metric.getOrigin())));
            verify(points).save(argThat(point -> point.getRemainingPoints()==5 && point.getMetric().getMetricId()==7L));
        }
        @Test void legacyActiveIsObservedAndUnchangedCaptureIsDeduplicated() {
            when(sprints.findByProject_ProjectIdAndState(10,SprintState.ACTIVE)).thenReturn(List.of(sprint));
            when(metrics.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
            service.captureActiveProject(10);
            var metric = ArgumentCaptor.forClass(SprintMetric.class); verify(metrics).saveAndFlush(metric.capture());
            assertThat(metric.getValue().getOrigin()).isEqualTo("OBSERVED");
            when(metrics.findBySprint_SprintId(100)).thenReturn(Optional.of(metric.getValue()));
            service.captureActiveProject(10); verify(points,times(1)).save(any());
        }
        @Test void finishFreezesDonePointsBeforeScopeChanges() {
            var issue = Fixtures.issue(5,10); issue.setStoryPoints(8); issue.setStatus(Fixtures.status(3,"Completed",StatusCategory.DONE));
            when(links.findBacklog(100)).thenReturn(List.of(link(issue)));
            var metric = new SprintMetric(); metric.setCommittedPoints(13); when(metrics.findBySprint_SprintId(100)).thenReturn(Optional.of(metric));
            service.finish(sprint); assertThat(metric.getCompletedPoints()).isEqualTo(8); assertThat(metric.getCommittedPoints()).isEqualTo(13); assertThat(metric.getFinishedAt()).isNotNull();
        }
        @Test void duplicateStartCannotOverwriteCommitment() {
            when(metrics.findBySprint_SprintId(100)).thenReturn(Optional.of(new SprintMetric()));
            assertThatThrownBy(() -> service.start(sprint)).isInstanceOf(ResponseStatusException.class); verifyNoInteractions(links,points);
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Reports {
        @Mock SprintRepository sprints; @Mock ProjectRepository projects; @Mock SprintMetricRepository metrics;
        @Mock SprintMetricPointRepository points; @Mock SprintMetricsService calculator; @Mock ProjectSecurity security;
        @InjectMocks ScrumReportService service;
        @Test void missingSprintDoesNotReadSnapshot() { assertThatThrownBy(() -> service.summary(99)).isInstanceOf(ResponseStatusException.class); verifyNoInteractions(metrics,points); }
        @Test void unauthorizedReadStopsBeforeSnapshot() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100,10,SprintState.CLOSED)));
            doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10,PermissionName.VIEW_PROJECT);
            assertThatThrownBy(() -> service.burndown(100)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); verifyNoInteractions(metrics,points);
        }
        @Test void legacyBurndownDoesNotInventHistory() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100,10,SprintState.CLOSED)));
            var result = service.burndown(100); assertThat(result.baselineAvailable()).isFalse(); assertThat(result.observedStart()).isNull(); assertThat(result.points()).isEmpty(); verifyNoInteractions(points);
        }
        @ParameterizedTest @ValueSource(ints={-1,0,51})
        void velocityLimitIsBounded(int limit) { assertThatThrownBy(() -> service.velocity(10,limit)).isInstanceOf(ResponseStatusException.class); verifyNoInteractions(projects,sprints); }
        @Test void velocityKeepsLegacyUnknownAndUsesFrozenCompletion() {
            var known=Fixtures.sprint(100,10,SprintState.CLOSED); var legacy=Fixtures.sprint(101,10,SprintState.CLOSED);
            when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
            when(sprints.findClosedForVelocity(eq(10),any(Pageable.class))).thenReturn(List.of(known,legacy));
            var metric=new SprintMetric(); metric.setSprint(known); metric.setOrigin("START_EVENT"); metric.setCompletedPoints(8L); metric.setCompletedCount(1L); metric.setCommittedPoints(13);
            when(metrics.findBySprint_SprintIdIn(List.of(100,101))).thenReturn(List.of(metric));
            var result=service.velocity(10,10); assertThat(result.get(0).completedPoints()).isEqualTo(8); assertThat(result.get(0).baselineAvailable()).isTrue();
            assertThat(result.get(1).completedPoints()).isNull(); assertThat(result.get(1).completionAvailable()).isFalse();
            verifyNoInteractions(calculator); verify(metrics,never()).save(any());
        }
    }
}
