package com.example.jira.regression;

import com.example.jira.dto.board.*;
import com.example.jira.dto.sprint.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScrumRegressionTest {
    @Nested @ExtendWith(MockitoExtension.class)
    class Sprints {
        @Mock jakarta.persistence.EntityManager entityManager;
        @Mock SprintRepository sprints;
        @Mock ProjectRepository projects;
        @Mock IssueRepository issues;
        @Mock SprintIssueRepository backlog;
        @Mock ProjectSecurity security;
        @InjectMocks SprintServiceImpl service;
        // Khóa project là bước mới của mọi mutation; lenient vì case thiếu quyền dừng trước khóa.
        @BeforeEach void projectLock() {
            lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
            lenient().when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 10)));
        }
        // Tạo sprint hợp lệ lưu trạng thái FUTURE và đúng project.
        @Test void createStartsInFutureState() {
            when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            when(sprints.save(any())).thenAnswer(i -> i.getArgument(0));
            var r = new CreateSprintRequest(); r.setProjectId(10); r.setSprintName("Sprint 1");
            var result = service.createSprint(r);
            assertThat(result.getState()).isEqualTo(SprintState.FUTURE); assertThat(result.getProjectId()).isEqualTo(10);
            assertThat(result.getCreatedAt()).isNotNull();
        }
        // Thiếu quyền không được bắt đầu hoặc ghi sprint.
        @Test void outsiderCannotStartSprint() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100, 10, SprintState.FUTURE)));
            assertThatThrownBy(() -> service.startSprint(100)).hasMessageContaining("quyền");
            verify(sprints, never()).save(any());
        }
        // ID sprint không tồn tại không được tạo liên kết backlog.
        @Test void missingSprintDoesNotAddIssue() {
            assertThatThrownBy(() -> service.addIssueToSprint(999, 5)).hasMessageContaining("sprint");
            verifyNoInteractions(backlog, issues);
        }
        // Thêm issue cùng project ghi khóa ghép sprint_id/issue_id và thời điểm thêm.
        @Test void addIssueStoresCompositeKey() {
            var s = Fixtures.sprint(100, 10, SprintState.FUTURE); var i = Fixtures.issue(5, 10);
            when(sprints.findById(100)).thenReturn(Optional.of(s));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            when(issues.findById(5)).thenReturn(Optional.of(i));
            service.addIssueToSprint(100, 5);
            var capture = ArgumentCaptor.forClass(SprintIssue.class); verify(backlog).save(capture.capture());
            assertThat(capture.getValue().getId()).isEqualTo(new SprintIssueId(100, 5));
            assertThat(capture.getValue().getSprint()).isSameAs(s); assertThat(capture.getValue().getIssue()).isSameAs(i);
            assertThat(capture.getValue().getAddedAt()).isNotNull();
        }
        // Gỡ issue chỉ tác động đúng cặp sprint/issue.
        @Test void removeUsesBothIds() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100, 10, SprintState.FUTURE)));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            service.removeIssueFromSprint(100, 5); verify(backlog).deleteBySprint_SprintIdAndIssue_IssueId(100, 5);
        }
        // Bắt đầu sprint FUTURE chuyển sang ACTIVE.
        @Test void startChangesFutureToActive() {
            var s = Fixtures.sprint(100, 10, SprintState.FUTURE);
            when(sprints.findById(100)).thenReturn(Optional.of(s));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            service.startSprint(100); assertThat(s.getState()).isEqualTo(SprintState.ACTIVE); verify(sprints).save(s);
        }
        // Hoàn tất giữ việc Done, chuyển việc chưa xong sang sprint đích cùng project.
        @Test void completeMovesOnlyUnfinishedIssues() {
            var s = Fixtures.sprint(100, 10, SprintState.ACTIVE); var next = Fixtures.sprint(101, 10, SprintState.FUTURE);
            var done = Fixtures.issue(5, 10); done.setStatus(Fixtures.status(3, "Done", StatusCategory.DONE));
            var todo = Fixtures.issue(6, 10);
            when(sprints.findById(100)).thenReturn(Optional.of(s)); when(sprints.findById(101)).thenReturn(Optional.of(next));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            when(backlog.findBySprint_SprintId(100)).thenReturn(List.of(link(s, done), link(s, todo)));
            service.completeSprint(100, 101);
            assertThat(s.getState()).isEqualTo(SprintState.CLOSED);
            verify(backlog).deleteBySprint_SprintIdAndIssue_IssueId(100, 6);
            verify(backlog, never()).deleteBySprint_SprintIdAndIssue_IssueId(100, 5);
            verify(backlog).save(argThat(si -> si.getSprint().getSprintId() == 101 && si.getIssue().getIssueId() == 6));
        }
        // BUG-003: issue project 20 không được đưa vào sprint project 10.
        @Test void rejectCrossProjectIssue() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100, 10, SprintState.FUTURE)));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            when(issues.findById(5)).thenReturn(Optional.of(Fixtures.issue(5, 20)));
            assertThatThrownBy(() -> service.addIssueToSprint(100, 5)).isInstanceOf(RuntimeException.class);
            verify(backlog, never()).save(any());
        }
        // BUG-004: sprint CLOSED phải giữ CLOSED, không cho mở lại bằng start.
        @Test void closedSprintCannotRestart() {
            var s = Fixtures.sprint(100, 10, SprintState.CLOSED);
            when(sprints.findById(100)).thenReturn(Optional.of(s));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            assertThatThrownBy(() -> service.startSprint(100)).isInstanceOf(RuntimeException.class);
            assertThat(s.getState()).isEqualTo(SprintState.CLOSED); verify(sprints, never()).save(any());
        }
        // BUG-005: nhóm DONE xác định hoàn thành, tên hiển thị tùy chỉnh không đổi nghĩa.
        @Test void doneCategoryMustRemainInCompletedSprint() {
            var s = Fixtures.sprint(100, 10, SprintState.ACTIVE); var done = Fixtures.issue(5, 10);
            done.setStatus(Fixtures.status(3, "Completed", StatusCategory.DONE));
            when(sprints.findById(100)).thenReturn(Optional.of(s));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            when(backlog.findBySprint_SprintId(100)).thenReturn(List.of(link(s, done)));
            service.completeSprint(100, null);
            verify(backlog, never()).deleteBySprint_SprintIdAndIssue_IssueId(anyInt(), anyInt());
        }
        // BUG-006: không sửa backlog khi sprint đã đóng.
        @Test void closedSprintRejectsNewIssue() {
            when(sprints.findById(100)).thenReturn(Optional.of(Fixtures.sprint(100, 10, SprintState.CLOSED)));
            when(security.hasPermission(10, PermissionName.MANAGE_SPRINT)).thenReturn(true);
            assertThatThrownBy(() -> service.addIssueToSprint(100, 5)).isInstanceOf(RuntimeException.class);
            verify(backlog, never()).save(any());
        }
        // Liên kết chỉ là dữ liệu test, chưa ghi DB; integration test kiểm tra transaction riêng.
        private SprintIssue link(Sprint s, Issue i) {
            var link = new SprintIssue(); link.setId(new SprintIssueId(s.getSprintId(), i.getIssueId()));
            link.setSprint(s); link.setIssue(i); return link;
        }
    }

    @Nested @ExtendWith(MockitoExtension.class)
    class Boards {
        @Mock BoardRepository boards;
        @Mock ProjectRepository projects;
        @Mock BoardColumnRepository columns;
        @Mock IssueStatusRepository statuses;
        @Mock BoardColumnStatusRepository mappings;
        @Mock ProjectSecurity security;
        @InjectMocks BoardServiceImpl service;
        // Board mẫu thuộc project 10; cột mẫu là của board 50.
        private Board board() { var b = new Board(); b.setBoardId(50); b.setProject(Fixtures.project(10)); return b; }
        private BoardColumn column(Board b, int id) { var c = new BoardColumn(); c.setColumnId(id); c.setBoard(b); return c; }
        // Tạo board khởi tạo đủ ba cột theo thứ tự.
        @Test void createBoardInitializesThreeColumns() {
            when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            when(boards.save(any())).thenAnswer(i -> { Board b = i.getArgument(0); b.setBoardId(50); return b; });
            when(boards.findById(50)).thenReturn(Optional.of(board()));
            var request = new CreateBoardRequest(); request.setProjectId(10); request.setBoardName("Team");
            service.createBoard(request);
            var capture = ArgumentCaptor.forClass(BoardColumn.class); verify(columns, times(3)).save(capture.capture());
            assertThat(capture.getAllValues()).extracting(BoardColumn::getColumnName).containsExactly("To Do", "In Progress", "Done");
            assertThat(capture.getAllValues()).extracting(BoardColumn::getPosition).containsExactly(1, 2, 3);
        }
        // Không có board phải dừng trước truy vấn cột.
        @Test void missingBoardDoesNotReadColumns() {
            assertThatThrownBy(() -> service.getBoard(999)).hasMessageContaining("bảng"); verifyNoInteractions(columns);
        }
        // Map trạng thái ghi đúng khóa ghép cột/trạng thái.
        @Test void mapStatusPersistsBothIds() {
            var c = column(board(), 60); var s = Fixtures.status(1, "To Do", StatusCategory.TO_DO);
            when(columns.findById(60)).thenReturn(Optional.of(c)); when(statuses.findById(1)).thenReturn(Optional.of(s));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            service.mapStatusToColumn(60, 1);
            verify(mappings).save(argThat(m -> m.getId().equals(new BoardColumnStatusId(60, 1)) && m.getIssueStatus() == s));
        }
        // Thiếu quyền quản lý board phải dừng trước ghi mapping.
        @Test void unauthorizedMappingDoesNotWrite() {
            when(columns.findById(60)).thenReturn(Optional.of(column(board(), 60)));
            assertThatThrownBy(() -> service.mapStatusToColumn(60, 1)).hasMessageContaining("quyền");
            verifyNoInteractions(mappings, statuses);
        }
        // Sắp xếp chỉ nhận cột của board đang thao tác.
        @Test void reorderRejectsForeignColumn() {
            when(boards.findById(50)).thenReturn(Optional.of(board()));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            var other = board(); other.setBoardId(51);
            when(columns.findById(61)).thenReturn(Optional.of(column(other, 61)));
            var r = new UpdateColumnOrderRequest(); r.setColumnId(61); r.setPosition(2);
            assertThatThrownBy(() -> service.updateColumnOrder(50, List.of(r))).hasMessageContaining("không thuộc");
            verify(columns, never()).save(any());
        }
        // Reorder đúng cập nhật vị trí cột và không sửa board khác.
        @Test void reorderUpdatesRequestedColumn() {
            var b = board(); var c = column(b, 60);
            when(boards.findById(50)).thenReturn(Optional.of(b)); when(columns.findById(60)).thenReturn(Optional.of(c));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            var r = new UpdateColumnOrderRequest(); r.setColumnId(60); r.setPosition(3);
            service.updateColumnOrder(50, List.of(r)); assertThat(c.getPosition()).isEqualTo(3); verify(columns).save(c);
        }
        // Unmap chỉ xóa liên kết tồn tại, không xóa trạng thái gốc.
        @Test void unmapDeletesLinkOnly() {
            var id = new BoardColumnStatusId(60, 1); var m = new BoardColumnStatus(); m.setId(id);
            when(columns.findById(60)).thenReturn(Optional.of(column(board(), 60)));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            when(mappings.findById(id)).thenReturn(Optional.of(m));
            service.unmapStatusFromColumn(60, 1); verify(mappings).delete(m); verifyNoInteractions(statuses);
        }
        // Thêm cột giữ board, tên và vị trí từ request.
        @Test void addColumnPreservesRequestedFields() {
            when(boards.findById(50)).thenReturn(Optional.of(board()));
            when(security.hasPermission(10, PermissionName.MANAGE_BOARD)).thenReturn(true);
            var r = new CreateColumnRequest(); r.setBoardId(50); r.setColumnName("Review"); r.setPosition(3);
            service.addColumn(r); verify(columns).save(argThat(c -> c.getColumnName().equals("Review") && c.getPosition() == 3));
        }
    }
}
