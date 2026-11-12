package com.example.jira.regression;
import com.example.jira.dto.issue.*;
import com.example.jira.dto.audit.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.service.*;
import com.example.jira.service.impl.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CollaborationAdminRegressionTest {
    @BeforeEach void identity() { Fixtures.login(1); }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Nested @ExtendWith(MockitoExtension.class)
    class Comments {
        @Mock IssueCommentRepository comments; @Mock IssueRepository issues; @Mock UserRepository users;
        @Mock ProjectSecurity projectSecurity; @Mock NotificationService notifications; @Mock ProjectRepository projects;
        @Mock EntityManager entityManager; @Mock AuditLogService audit;
        @InjectMocks IssueCommentServiceImpl service;
        IssueComment comment;
        @BeforeEach void row() {
            var issue=Fixtures.issue(5,10); comment=new IssueComment(); comment.setCommentId(7); comment.setIssue(issue); comment.setUser(Fixtures.user(1)); comment.setVersion(2L); comment.setCommentText("Old");
            lenient().when(issues.findById(5)).thenReturn(Optional.of(issue)); lenient().when(comments.findById(7)).thenReturn(Optional.of(comment));
            lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
        }
        @Test void ownerEditReturnsFlushedVersionWithoutNewNotification() {
            doAnswer(call -> { comment.setVersion(3L); return null; }).when(entityManager).flush();
            var result=service.updateComment(5,7,new UpdateCommentRequest("Changed",2L));
            assertThat(result.getVersion()).isEqualTo(3); assertThat(result.getCommentText()).isEqualTo("Changed"); assertThat(result.getUpdatedAt()).isNotNull(); verifyNoInteractions(notifications);
        }
        @Test void staleCommentCannotWriteOrDelete() {
            fails(() -> service.updateComment(5,7,new UpdateCommentRequest("Changed",1L)),409);
            fails(() -> service.deleteComment(5,7,1L),409); assertThat(comment.getCommentText()).isEqualTo("Old");
            verify(comments,never()).save(any()); verify(comments,never()).delete(any()); verifyNoInteractions(audit);
        }
        @Test void foreignCommentIsNotFoundAndNotLocked() {
            comment.setIssue(Fixtures.issue(6,20)); fails(() -> service.deleteComment(5,7,2L),404); verifyNoInteractions(projects);
        }
        @Test void nonOwnerWithoutEditPermissionIsForbidden() {
            comment.setUser(Fixtures.user(2)); fails(() -> service.deleteComment(5,7,2L),403); verifyNoInteractions(projects,audit);
        }
        @Test void editorCanModerateOtherAuthorsComment() {
            comment.setUser(Fixtures.user(2)); when(projectSecurity.hasPermission(10,PermissionName.EDIT_ISSUE)).thenReturn(true);
            service.deleteComment(5,7,2L); verify(comments).delete(comment); verify(audit).logProjectAction(eq("DELETE_COMMENT"),eq(EntityType.COMMENT),eq(7),eq(10),anyString());
        }
        @Test void removedAuthorNeedsProjectViewPermission() {
            doThrow(new org.springframework.security.access.AccessDeniedException("removed")).when(projectSecurity).requirePermission(10,PermissionName.VIEW_PROJECT);
            assertThatThrownBy(() -> service.deleteComment(5,7,2L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); verifyNoInteractions(comments);
        }
        @Test void invalidTextAndMissingVersionStopBeforeRead() {
            fails(() -> service.updateComment(5,7,new UpdateCommentRequest(" ",2L)),400);
            fails(() -> service.updateComment(5,7,new UpdateCommentRequest("x".repeat(10001),2L)),400);
            fails(() -> service.deleteComment(5,7,null),400); verifyNoInteractions(issues,comments);
        }
        @Test void legacyAuthorIsNullableInResponse() {
            comment.setUser(null); var response=com.example.jira.mapper.IssueCommentMapper.toResponse(comment); assertThat(response.getUserId()).isNull();
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Inbox {
        @Mock NotificationRepository notifications; @Mock GlobalSecurity identity;
        @InjectMocks NotificationInboxService service;
        @BeforeEach void caller() { when(identity.currentUserId()).thenReturn(1); }
        @Test void readAllUsesCallerAndReturnsAffectedRows() { when(notifications.markAllRead(1)).thenReturn(2); assertThat(service.readAll()).isEqualTo(2); }
        @Test void foreignNotificationCannotBeDeleted() {
            var notification=new Notification(); notification.setUser(Fixtures.user(2)); when(notifications.findById(7)).thenReturn(Optional.of(notification));
            fails(() -> service.delete(7),403); verify(notifications,never()).delete(any());
        }
        @Test void ownerCanDeleteAndMissingIdIsNotFound() {
            var notification=new Notification(); notification.setUser(Fixtures.user(1)); when(notifications.findById(7)).thenReturn(Optional.of(notification));
            service.delete(7); verify(notifications).delete(notification); fails(() -> service.delete(8),404);
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class AdminQueries {
        @Mock AuditLogRepository audits; @Mock UserRepository users; @Mock UserRoleAssignmentRepository assignments;
        @Mock ProjectRepository projects; @Mock IssueRepository issues; @Mock SprintRepository sprints; @Mock GlobalSecurity global;
        @InjectMocks AdminQueryService service;
        @Test void nonAdminCannotReadLogsOrOverview() {
            doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(global).assertSystemAdmin();
            assertThatThrownBy(() -> service.audit(new AuditSearchRequest())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(service::overview).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(audits,users,assignments,projects,issues,sprints);
        }
        @Test void legacySystemActorIsNullAndPageTotalsArePreserved() {
            var log=new AuditLog(); log.setAuditId(7); log.setAction("SYSTEM"); log.setEntityType(EntityType.PROJECT); log.setEntityId(10);
            when(audits.findAll(any(Specification.class),any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(log),call.getArgument(1),2));
            var request=new AuditSearchRequest(); request.setSize(1); var result=service.audit(request);
            assertThat(result.totalItems()).isEqualTo(2); assertThat(result.items()).singleElement().satisfies(entry -> assertThat(entry.actorId()).isNull());
        }
        @Test void ambiguousEntityDateAndHugeOffsetAreRejected() {
            var request=new AuditSearchRequest(); request.setEntityId(5); fails(() -> service.audit(request),400);
            request.setEntityType(EntityType.ISSUE); request.setCreatedFrom(java.time.LocalDateTime.of(2026,2,1,0,0)); request.setCreatedTo(java.time.LocalDateTime.of(2026,1,1,0,0));
            fails(() -> service.audit(request),400); request.setCreatedFrom(null); request.setCreatedTo(null); request.setPage(Integer.MAX_VALUE);
            fails(() -> service.audit(request),400); verifyNoInteractions(audits);
        }
    }
    private void fails(Runnable call,int status) { assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode().value()).isEqualTo(status)); }
}
