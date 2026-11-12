package com.example.jira.regression;

import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Kiểm các response có dữ liệu thật, đặc biệt những ID có thể bị nhầm giữa các quan hệ. */
class MapperRegressionTest {
    // Client phải lấy ID loại từ JSON để gán vào project, không chỉ nhận tên hiển thị.
    @Test void issueTypeResponseKeepsDatabaseId() {
        var type=new IssueType(); type.setIssueTypeId(7); type.setTypeName("Task");
        assertThat(IssueTypeMapper.toResponse(type).getIssueTypeId()).isEqualTo(7);
    }
    // Mapping member giữ user ID khác role ID và không lấy nhầm project lead.
    @Test void memberResponsePreservesUserAndRole() {
        var m = new ProjectMember(); m.setProject(Fixtures.project(10)); m.setUser(Fixtures.user(2));
        m.setRole(Fixtures.role(7, "DEVELOPER"));
        var response = ProjectMemberMapper.toResponse(m);
        assertThat(response.getUserId()).isEqualTo(2); assertThat(response.getRoleId()).isEqualTo(7);
        assertThat(response.getEmail()).isEqualTo("user2@test.invalid"); assertThat(response.getRole()).isEqualTo("DEVELOPER");
    }
    // Loại issue của project phải giữ đủ hai ID và cờ default.
    @Test void projectTypeResponsePreservesScopeAndDefaultFlag() {
        var t = new IssueType(); t.setIssueTypeId(7); t.setTypeName("Bug");
        var link = new ProjectIssueType(new ProjectIssueTypeId(10, 7), Fixtures.project(10), t, true);
        var response = ProjectIssueTypeMapper.toResponse(link);
        assertThat(response.getProjectId()).isEqualTo(10); assertThat(response.getIssueTypeId()).isEqualTo(7);
        assertThat(response.getIssueTypeName()).isEqualTo("Bug"); assertThat(response.isDefault()).isTrue();
    }
    // Board trả đúng status mapping và vị trí, không đảo column ID với status ID.
    @Test void boardResponseIncludesMappedStatusIds() {
        var b = new Board(); b.setBoardId(50); b.setProject(Fixtures.project(10));
        var c = new BoardColumn(); c.setColumnId(60); c.setPosition(2); c.setColumnName("Review"); c.setWipLimit(4);
        var mapping = new BoardColumnStatus(); mapping.setBoardColumn(c);
        mapping.setIssueStatus(Fixtures.status(3, "Reviewing", StatusCategory.IN_PROGRESS)); c.setBoardColumnStatuses(Set.of(mapping));
        var response = BoardMapper.toResponse(b, List.of(c));
        assertThat(response.getBoardId()).isEqualTo(50); assertThat(response.getProjectId()).isEqualTo(10);
        var col = response.getColumns().get(0); assertThat(col.getColumnId()).isEqualTo(60);
        assertThat(col.getMappedStatusIds()).containsExactly(3); assertThat(col.getPosition()).isEqualTo(2); assertThat(col.getWipLimit()).isEqualTo(4);
    }
    // Board chưa có cột trả danh sách rỗng, không null.
    @Test void boardWithoutColumnsHasEmptyList() {
        var b = new Board(); assertThat(BoardMapper.toResponse(b, null).getColumns()).isEmpty();
    }
    // Notification không có issue/actor hợp lệ cho sự kiện hệ thống.
    @Test void notificationAllowsAbsentIssueAndActor() {
        var n = new Notification(); n.setNotificationId(8); n.setUser(Fixtures.user(2)); n.setMessage("Welcome");
        var response = NotificationMapper.toResponse(n);
        assertThat(response.getUserId()).isEqualTo(2); assertThat(response.getIssueId()).isNull(); assertThat(response.getActorId()).isNull();
    }
    // Notification phân biệt người nhận, người thao tác và issue liên quan.
    @Test void notificationPreservesRecipientActorAndIssue() {
        var n = new Notification(); n.setUser(Fixtures.user(2)); n.setActor(Fixtures.user(1)); n.setIssue(Fixtures.issue(5, 10));
        n.setNotificationType(NotificationType.ISSUE_ASSIGNED); n.setRead(true);
        var response = NotificationMapper.toResponse(n);
        assertThat(response.getUserId()).isEqualTo(2); assertThat(response.getActorId()).isEqualTo(1);
        assertThat(response.getIssueId()).isEqualTo(5); assertThat(response.isRead()).isTrue();
    }
    // Issue đủ quan hệ phải trả đúng ID, story point và tên trạng thái.
    @Test void issueResponsePreservesIdentityAndEstimate() {
        var i = Fixtures.issue(5, 10); i.setStoryPoints(8);
        var type = new IssueType(); type.setIssueTypeId(23); type.setTypeName("Cùng tên có thể đổi"); i.setIssueType(type);
        var response = IssueMapper.toResponse(i);
        assertThat(response.getIssueId()).isEqualTo(5); assertThat(response.getProjectId()).isEqualTo(10);
        assertThat(response.getAssigneeId()).isEqualTo(2); assertThat(response.getReporterId()).isEqualTo(3);
        assertThat(response.getStoryPoints()).isEqualTo(8); assertThat(response.getStatusName()).isEqualTo("To Do");
        assertThat(response.getStatusId()).isEqualTo(1); assertThat(response.getIssueTypeId()).isEqualTo(23);
    }

    // Quan hệ thiếu giữ null; không cấp ID giả làm UI cập nhật nhầm bản ghi cấu hình.
    @Test void issueConfigurationIdsRemainNullable() {
        var i = Fixtures.issue(5, 10); i.setStatus(null); i.setIssueType(null);
        var response = IssueMapper.toResponse(i);
        assertThat(response.getStatusId()).isNull(); assertThat(response.getIssueTypeId()).isNull();
    }
}
