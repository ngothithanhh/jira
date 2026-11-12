package com.example.jira.regression;

import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.impl.BacklogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BacklogRegressionTest {
    @Mock ProjectRepository projects;
    @Mock SprintRepository sprints;
    @Mock IssueRepository issues;
    @Mock SprintIssueRepository links;
    @Mock ProjectSecurity security;
    @Mock EntityManager entityManager;
    @Mock com.example.jira.service.AuditLogService audit;
    @InjectMocks BacklogService service;

    // Scope là project 10; chỉ mock dữ liệu chung vì nhánh 400/403 có thể dừng trước truy vấn.
    @BeforeEach void project() {
        lenient().when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
        lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
    }
    // Project không tồn tại phải báo 404 thay vì trả backlog rỗng khiến người gọi hiểu sai.
    @Test void missingProjectIsNotFound() { fails(() -> service.product(99), HttpStatus.NOT_FOUND); verifyNoInteractions(issues); }
    // Người ngoài không được đọc query chứa nội dung issue.
    @Test void deniedReadStopsBeforeIssueQuery() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(security).requirePermission(10, PermissionName.VIEW_PROJECT);
        assertThatThrownBy(() -> service.product(10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(issues);
    }
    // Rank đã chốt thắng priority; rank null dùng priority rồi number, kể cả giá trị priority chưa xác định.
    @Test void productOrderIsStableAndPreservesOptionalRelations() {
        var ranked = Fixtures.issue(5, 10); ranked.setPriority("LOW"); ranked.setBacklogPosition(0);
        var high = Fixtures.issue(6, 10); high.setPriority("HIGH"); high.setAssignee(null); high.setParentIssue(null);
        var unknown = Fixtures.issue(7, 10); unknown.setPriority(null); unknown.setStatus(null);
        when(issues.findProductBacklog(10)).thenReturn(List.of(unknown, high, ranked));
        assertThat(service.product(10)).extracting(IssueResponse::getIssueId).containsExactly(5, 6, 7);
        verify(issues, never()).saveAll(any());
    }
    // Chưa có quyền ghi không được lấy khóa hay lưu rank.
    @Test void deniedReorderDoesNotLock() {
        fails(() -> service.orderProduct(10, List.of()), HttpStatus.FORBIDDEN);
        verify(projects, never()).findByIdForUpdate(anyInt()); verifyNoInteractions(issues);
    }
    // MANAGE_SPRINT cũng được sắp product backlog khi không có EDIT_ISSUE.
    @Test void sprintManagerCanReorderProduct() {
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(false);
        when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
        var first = Fixtures.issue(5, 10); var second = Fixtures.issue(6, 10);
        when(issues.findProductBacklog(10)).thenReturn(List.of(first, second));
        assertThat(service.orderProduct(10, List.of(6, 5))).extracting(IssueResponse::getIssueId).containsExactly(6, 5);
        assertThat(second.getBacklogPosition()).isZero(); assertThat(first.getBacklogPosition()).isEqualTo(1);
    }
    // Snapshot thiếu/thừa/sai scope đều dừng trước ghi bất kỳ rank nào.
    @ParameterizedTest @ValueSource(strings = {"missing", "extra", "foreign"})
    void scopeMismatchDoesNotPartiallyUpdate(String kind) {
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        var first = Fixtures.issue(5, 10); first.setBacklogPosition(8);
        when(issues.findProductBacklog(10)).thenReturn(List.of(first, Fixtures.issue(6, 10)));
        List<Integer> requested = switch (kind) { case "missing" -> List.of(5); case "extra" -> List.of(5,6,7); default -> List.of(5,99); };
        fails(() -> service.orderProduct(10, requested), HttpStatus.CONFLICT);
        assertThat(first.getBacklogPosition()).isEqualTo(8); verify(issues, never()).saveAll(any());
    }
    // Dữ liệu không hợp lệ bị chặn tại service kể cả không qua Bean Validation của HTTP.
    @Test void invalidIdsStopBeforeDatabase() {
        for (List<Integer> ids : Arrays.asList(null, List.of(5,5), List.of(0), List.of(-1), Arrays.asList(5,null))) {
            fails(() -> service.orderProduct(10, ids), HttpStatus.BAD_REQUEST);
            fails(() -> service.orderSprint(100, ids), HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(projects, sprints, issues, links);
    }
    // Backlog rỗng nhận [] để client không cần nhánh đặc biệt.
    @Test void emptyBacklogAcceptsEmptyOrder() {
        when(security.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
        assertThat(service.orderProduct(10, List.of())).isEmpty();
    }
    // Không dùng rank product để sắp sprint; liên kết mới chưa có vị trí nằm sau các rank đã chốt.
    @Test void sprintUsesItsOwnPositions() {
        var sprint = Fixtures.sprint(100,10,SprintState.CLOSED);
        when(sprints.findById(100)).thenReturn(Optional.of(sprint));
        var a = link(sprint,5); a.setPosition(1); a.getIssue().setBacklogPosition(0);
        var b = link(sprint,6); b.setPosition(0); var c = link(sprint,7);
        when(links.findBacklog(100)).thenReturn(List.of(c,a,b));
        assertThat(service.sprint(100)).extracting(IssueResponse::getIssueId).containsExactly(6,5,7);
    }
    // Dữ liệu legacy chéo project không được lộ ra hoặc sửa vị trí.
    @Test void corruptSprintMembershipIsConflict() {
        var sprint = Fixtures.sprint(100,10,SprintState.FUTURE); var bad = link(sprint,5);
        bad.getIssue().setProject(Fixtures.project(20));
        when(sprints.findById(100)).thenReturn(Optional.of(sprint)); when(links.findBacklog(100)).thenReturn(List.of(bad));
        fails(() -> service.sprint(100), HttpStatus.CONFLICT);
        fails(() -> service.orderSprint(100, List.of(5)), HttpStatus.CONFLICT);
        verify(links, never()).saveAll(any());
    }
    // Refresh dưới khóa phải diễn ra trước kiểm CLOSED, không tin state đã đọc trước khi chờ.
    @Test void newlyClosedSprintCannotBeReordered() {
        var sprint = Fixtures.sprint(100,10,SprintState.FUTURE); when(sprints.findById(100)).thenReturn(Optional.of(sprint));
        doAnswer(call -> { sprint.setState(SprintState.CLOSED); return null; }).when(entityManager).refresh(sprint);
        fails(() -> service.orderSprint(100, List.of()), HttpStatus.CONFLICT);
        verifyNoInteractions(links);
        var sequence = inOrder(projects, entityManager); sequence.verify(projects).findByIdForUpdate(10); sequence.verify(entityManager).refresh(sprint);
    }
    // Reorder không đổi addedAt, chỉ thay position trên đúng cặp sprint/issue.
    @Test void sprintReorderPreservesAddedAt() {
        var sprint = Fixtures.sprint(100,10,SprintState.ACTIVE); when(sprints.findById(100)).thenReturn(Optional.of(sprint));
        var link = link(sprint,5); link.setAddedAt(java.time.LocalDateTime.of(2026,1,1,0,0)); var added = link.getAddedAt();
        when(links.findBacklog(100)).thenReturn(List.of(link));
        assertThat(service.orderSprint(100,List.of(5))).hasSize(1);
        assertThat(link.getPosition()).isZero(); assertThat(link.getAddedAt()).isEqualTo(added);
    }
    // Sprint không tồn tại phải dừng trước truy vấn backlog.
    @Test void missingSprintIsNotFound() { fails(() -> service.sprint(999), HttpStatus.NOT_FOUND); verifyNoInteractions(links); }

    private SprintIssue link(Sprint sprint,int id) {
        var link = new SprintIssue(); link.setId(new SprintIssueId(sprint.getSprintId(),id));
        link.setSprint(sprint); link.setIssue(Fixtures.issue(id,10)); return link;
    }
    private void fails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
