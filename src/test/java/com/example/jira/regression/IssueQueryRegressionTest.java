package com.example.jira.regression;

import com.example.jira.dto.issue.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.AuditLogService;
import com.example.jira.service.impl.IssueQueryService;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class IssueQueryRegressionTest {
    @Mock IssueRepository issues;
    @Mock ProjectRepository projects;
    @Mock SprintRepository sprints;
    @Mock ProjectSecurity security;
    @Mock EntityManager entityManager;
    @Mock AuditLogService audit;
    @Mock com.example.jira.service.impl.SprintMetricsService metrics;
    @Mock TypedQuery<Long> count;
    @Mock Query delete;
    @InjectMocks IssueQueryService service;
    Issue issue;
    @BeforeEach void fixture() {
        issue = Fixtures.issue(5,10); issue.setVersion(2L);
        lenient().when(issues.findById(5)).thenReturn(Optional.of(issue));
        lenient().when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
        lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
    }
    @Test void detailPreservesNullRelationsAndVersion() {
        issue.setAssignee(null); issue.setParentIssue(null);
        var result = service.detail(5); assertThat(result.getAssigneeId()).isNull(); assertThat(result.getVersion()).isEqualTo(2);
        verify(security).requirePermission(10,PermissionName.VIEW_PROJECT);
    }
    @Test void missingDetailIsNotFound() { fails(() -> service.detail(99),HttpStatus.NOT_FOUND); verifyNoInteractions(security); }
    @Test void deniedSearchDoesNotQueryIssueContents() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10,PermissionName.VIEW_PROJECT);
        assertThatThrownBy(() -> service.search(10,new IssueSearchRequest())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(issues,never()).findAll(any(Specification.class),any(Pageable.class));
    }
    @Test void searchUsesPageAndStableSortAndReportsTotals() {
        var request = new IssueSearchRequest(); request.setPage(1); request.setSize(2); request.setSort("summary"); request.setDirection("asc");
        when(issues.findAll(any(Specification.class),any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(issue),call.getArgument(1),3));
        var result = service.search(10,request); assertThat(result.page()).isEqualTo(1); assertThat(result.totalItems()).isEqualTo(3); assertThat(result.totalPages()).isEqualTo(2);
        var pageable = ArgumentCaptor.forClass(Pageable.class); verify(issues).findAll(any(Specification.class),pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("summary","issueId"));
    }
    @ParameterizedTest @ValueSource(strings = {"page","size","sort","direction","keyword","assignee","id"})
    void invalidSearchIsBadRequest(String kind) {
        var request = new IssueSearchRequest();
        switch (kind) {
            case "page" -> request.setPage(-1); case "size" -> request.setSize(101); case "sort" -> request.setSort("passwordHash");
            case "direction" -> request.setDirection("sideways"); case "keyword" -> request.setKeyword("x".repeat(201));
            case "assignee" -> { request.setUnassigned(true); request.setAssigneeId(2); } default -> request.setTypeId(0);
        }
        fails(() -> service.search(10,request),HttpStatus.BAD_REQUEST); verifyNoInteractions(projects);
    }
    @Test void foreignSprintCannotChangeSearchScope() {
        when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100,20,SprintState.FUTURE)));
        var request = new IssueSearchRequest(); request.setSprintId(100);
        fails(() -> service.search(10,request),HttpStatus.BAD_REQUEST);
        verify(issues,never()).findAll(any(Specification.class),any(Pageable.class));
    }
    @Test void staleDeleteDoesNotTouchDependencies() { fails(() -> service.delete(5,1L),HttpStatus.CONFLICT); verify(entityManager,never()).createQuery(anyString(),eq(Long.class)); verify(issues,never()).delete(any(Issue.class)); }
    @Test void missingDeleteVersionStopsBeforeRead() { fails(() -> service.delete(5,null),HttpStatus.BAD_REQUEST); verifyNoInteractions(issues); }
    @Test void deleteWithChildrenIsConflict() {
        when(issues.existsByParentIssue_IssueId(5)).thenReturn(true);
        fails(() -> service.delete(5,2L),HttpStatus.CONFLICT); verify(issues,never()).delete(any(Issue.class));
    }
    @Test void attachmentRequiresSeparateCleanup() {
        attachmentCount(1L); fails(() -> service.delete(5,2L),HttpStatus.CONFLICT);
        verify(entityManager,never()).createQuery(anyString()); verifyNoInteractions(audit);
    }
    @Test void eligibleDeleteCleansOnlyIssueThenAudits() {
        attachmentCount(0L); when(entityManager.createQuery(startsWith("delete from "))).thenReturn(delete);
        when(delete.setParameter("id",5)).thenReturn(delete);
        service.delete(5,2L);
        verify(delete,times(7)).setParameter("id",5); verify(delete,times(7)).executeUpdate();
        var order = inOrder(issues,audit); order.verify(issues).delete(issue); order.verify(audit).logAction(eq("DELETE"),eq(EntityType.ISSUE),eq(5),contains(issue.getIssueKey()));
    }
    private void attachmentCount(long value) {
        when(entityManager.createQuery(startsWith("select count"),eq(Long.class))).thenReturn(count);
        when(count.setParameter("id",5)).thenReturn(count); when(count.getSingleResult()).thenReturn(value);
    }
    private void fails(Runnable action,HttpStatus status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode()).isEqualTo(status)); }
}
