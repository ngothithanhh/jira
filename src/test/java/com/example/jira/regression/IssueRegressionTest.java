package com.example.jira.regression;

import com.example.jira.dto.issue.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.NotificationService;
import com.example.jira.service.impl.IssueServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueRegressionTest {
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock com.example.jira.service.impl.BoardWipPolicy boardWipPolicy;
    @Mock ProjectSecurity security;
    @Mock ProjectRepository projects;
    @Mock IssueTypeRepository types;
    @Mock ProjectIssueTypeRepository projectTypes;
    @Mock IssueRepository issues;
    @Mock UserRepository users;
    @Mock IssueStatusRepository statuses;
    @Mock ProjectMemberRepository members;
    @Mock IssueTransitionRepository transitions;
    @Mock NotificationService notifications;
    @Mock WorkflowRuleRepository workflow;
    @InjectMocks IssueServiceImpl service;
    @BeforeEach void identifyCaller() {
        Fixtures.login(1);
        // Case thiếu quyền/ID dừng trước khóa; case ghi hợp lệ phải có project để lấy khóa chung.
        lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
    }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    // Thiếu quyền tạo phải bị chặn trước khi đọc cấu hình và ghi issue.
    @Test void unauthorizedCreateDoesNotWrite() {
        var request = new CreateIssueRequest(); request.setProjectId(10);
        assertThatThrownBy(() -> service.createIssue(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(issues, never()).save(any()); verifyNoInteractions(projects, types, projectTypes);
    }
    // ID project hợp lệ là điều kiện đầu tiên sau quyền tạo.
    @Test void missingProjectStopsCreate() {
        var r = new CreateIssueRequest(); r.setProjectId(10);
        when(security.hasPermission(10, PermissionName.CREATE_ISSUE)).thenReturn(true);
        when(projects.findByIdForUpdate(10)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createIssue(r)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(projects).findByIdForUpdate(10);
        verify(issues, never()).save(any());
    }
    // Issue không tồn tại phải dừng trước khi kiểm quyền cập nhật.
    @ParameterizedTest @ValueSource(ints = {0, -1, 999})
    void missingIssueRejectsUpdate(int id) {
        assertThatThrownBy(() -> service.updateIssue(id, new UpdateIssueRequest())).isInstanceOf(RuntimeException.class);
        verify(issues, never()).save(any()); verifyNoInteractions(security);
    }
    // Chỉnh sửa chỉ áp dụng trường có trong request, giữ các trường khác.
    @Test void partialUpdatePreservesOtherFields() {
        var issue = Fixtures.issue(5, 10); issue.setDescription("keep"); issue.setPriority("HIGH");
        when(issues.findById(5)).thenReturn(Optional.of(issue));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        when(issues.save(issue)).thenReturn(issue);
        var r = new UpdateIssueRequest(); r.setSummary("changed"); r.setStoryPoints(8);
        var result = service.updateIssue(5, r);
        assertThat(result.getSummary()).isEqualTo("changed"); assertThat(result.getStoryPoints()).isEqualTo(8);
        assertThat(issue.getDescription()).isEqualTo("keep"); assertThat(issue.getPriority()).isEqualTo("HIGH");
        assertThat(issue.getUpdatedAt()).isNotNull();
    }
    // Người ngoài project không được lưu chỉnh sửa hoặc phát thông báo.
    @Test void unauthorizedUpdateDoesNotWrite() {
        when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
        assertThatThrownBy(() -> service.updateIssue(5, new UpdateIssueRequest())).isInstanceOf(RuntimeException.class);
        verify(issues, never()).save(any()); verifyNoInteractions(notifications);
    }
    // Người nhận phải thuộc cùng project với issue.
    @Test void cannotAssignOutsider() {
        when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        assertThatThrownBy(() -> service.assignIssue(5, 77)).isInstanceOf(RuntimeException.class);
        verify(members).findByProject_ProjectIdAndUser_UserId(10, 77);
        verify(issues, never()).save(any()); verifyNoInteractions(notifications);
    }
    // Gán đúng thành viên phải lưu người nhận và gửi thông báo tới user đó.
    @Test void assignMemberNotifiesCorrectRecipient() {
        var issue = Fixtures.issue(5, 10); var m = new ProjectMember(); m.setUser(Fixtures.user(2));
        when(issues.findById(5)).thenReturn(Optional.of(issue));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        when(members.findByProject_ProjectIdAndUser_UserId(10, 2)).thenReturn(Optional.of(m));
        when(issues.save(issue)).thenReturn(issue);
        service.assignIssue(5, 2);
        assertThat(issue.getAssignee().getUserId()).isEqualTo(2);
        verify(notifications).sendNotification(eq(2), eq(5), eq(1), eq(NotificationType.ISSUE_ASSIGNED), contains(issue.getIssueKey()));
    }
    // Gửi lại trạng thái hiện tại không được ghi thêm lịch sử hay notification.
    @Test void unchangedStatusDoesNotWriteHistory() {
        var issue = Fixtures.issue(5, 10);
        when(issues.findById(5)).thenReturn(Optional.of(issue));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        when(statuses.findById(1)).thenReturn(Optional.of(issue.getStatus()));
        service.changeIssueStatus(5, 1);
        verifyNoInteractions(transitions, notifications, workflow); verify(issues, never()).save(any());
    }
    // Workflow không cho chuyển phải giữ nguyên trạng thái và không ghi lịch sử.
    @Test void forbiddenTransitionDoesNotWrite() {
        var issue = Fixtures.issue(5, 10);
        when(issues.findById(5)).thenReturn(Optional.of(issue));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        when(statuses.findById(2)).thenReturn(Optional.of(Fixtures.status(2, "Doing", StatusCategory.IN_PROGRESS)));
        assertThatThrownBy(() -> service.changeIssueStatus(5, 2)).isInstanceOf(RuntimeException.class);
        assertThat(issue.getStatus().getStatusId()).isEqualTo(1); verifyNoInteractions(transitions, notifications);
    }
    // Chuyển hợp lệ ghi đúng nguồn, đích và người thao tác, không gửi trùng cho cùng user.
    @Test void transitionWritesHistoryAndDeduplicatesRecipients() {
        var issue = Fixtures.issue(5, 10); issue.setAssignee(issue.getReporter());
        var next = Fixtures.status(2, "Doing", StatusCategory.IN_PROGRESS);
        when(issues.findById(5)).thenReturn(Optional.of(issue));
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        when(statuses.findById(2)).thenReturn(Optional.of(next));
        when(workflow.existsByFromStatus_StatusIdAndToStatus_StatusId(1, 2)).thenReturn(true);
        when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
        when(issues.save(issue)).thenReturn(issue);
        service.changeIssueStatus(5, 2);
        var capture = ArgumentCaptor.forClass(IssueTransition.class); verify(transitions).save(capture.capture());
        assertThat(capture.getValue().getFromStatus().getStatusId()).isEqualTo(1);
        assertThat(capture.getValue().getToStatus()).isSameAs(next);
        assertThat(capture.getValue().getUser().getUserId()).isEqualTo(1);
        verify(notifications).sendNotification(eq(3), eq(5), eq(1), eq(NotificationType.ISSUE_STATUS_CHANGED), anyString());
        verifyNoMoreInteractions(notifications); assertThat(issue.getStatus()).isSameAs(next);
    }
    // Đọc issue theo project cần VIEW_PROJECT trước khi truy vấn danh sách.
    @Test void outsiderCannotReadIssues() {
        assertThatThrownBy(() -> service.getIssuesByProject(20)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(issues);
    }
    // Danh sách trả đúng issue đã truy vấn theo project, không dùng findAll.
    @Test void readIssuesUsesProjectScope() {
        when(security.hasPermission(10, PermissionName.VIEW_PROJECT)).thenReturn(true);
        when(issues.findByProject_ProjectId(10)).thenReturn(List.of(Fixtures.issue(5, 10)));
        assertThat(service.getIssuesByProject(10)).extracting(IssueResponse::getIssueId).containsExactly(5);
    }

    // BUG-001: issue chưa được giao hoặc không có cha vẫn phải chuyển được sang response.
    @ParameterizedTest @ValueSource(strings = {"assignee", "parent"})
    void optionalRelationsMustNotCrashMapper(String relation) {
        var issue = Fixtures.issue(5, 10);
        if (relation.equals("assignee")) issue.setAssignee(null); else issue.setParentIssue(null);
        assertThatCode(() -> IssueMapper.toResponse(issue)).doesNotThrowAnyException();
    }
    // BUG-002: tạo issue thường không có assignee phải thành công và sinh mã theo project.
    @Test void createOrdinaryIssueReturnsGeneratedKey() {
        var r = new CreateIssueRequest(); r.setProjectId(10); r.setIssueTypeId(7); r.setSummary("New task");
        when(security.hasPermission(10, PermissionName.CREATE_ISSUE)).thenReturn(true);
        when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
        var type = new IssueType(); type.setIssueTypeId(7);
        when(types.findById(7)).thenReturn(Optional.of(type));
        when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
        when(projectTypes.existsById(new ProjectIssueTypeId(10, 7))).thenReturn(true);
        when(statuses.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(Fixtures.status(1, "To Do", StatusCategory.TO_DO)));
        when(issues.findMaxIssueNumberByProjectId(10)).thenReturn(Optional.of(4));
        when(issues.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createIssue(r).getIssueKey()).isEqualTo("P10-5");
    }
}
