package com.example.jira.regression;

import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;

/** Dữ liệu giả chỉ dùng trong test. Project 10 và 20 là hai dự án độc lập. */
public final class Fixtures {
    private Fixtures() { }
    // Gắn user vào SecurityContext để service dùng đúng danh tính, không mock SecurityUtils.
    public static void login(int id) {
        var principal = new UserPrincipal(id, "user" + id + "@test.invalid", "hash", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    // User ID 1 là người gọi; ID 2 và 3 dùng làm người nhận việc và người báo cáo.
    public static User user(int id) {
        var u = new User(); u.setUserId(id); u.setEmail("user" + id + "@test.invalid");
        u.setUserName("user" + id); u.setFullName("User " + id); u.setPasswordHash("hash");
        return u;
    }
    // Mỗi project có lead riêng để các case truy cập chéo không vô tình được quyền lead.
    public static Project project(int id) {
        var p = new Project(); p.setProjectId(id); p.setProjectName("Project " + id);
        p.setProjectKey("P" + id); p.setLead(user(99)); return p;
    }
    // Tạo trạng thái có cả tên hiển thị lẫn nhóm nghiệp vụ để phát hiện việc so sánh nhầm tên.
    public static IssueStatus status(int id, String name, StatusCategory category) {
        var s = new IssueStatus(); s.setStatusId(id); s.setStatusName(name); s.setStatusCategory(category); return s;
    }
    // Issue mẫu đủ quan hệ; từng test chủ động bỏ quan hệ để kiểm tra dữ liệu tùy chọn.
    public static Issue issue(int id, int projectId) {
        var i = new Issue(); i.setIssueId(id); i.setProject(project(projectId));
        i.setSummary("Task " + id); i.setIssueKey("P" + projectId + "-" + id);
        i.setIssueNumber(id); i.setReporter(user(3)); i.setAssignee(user(2));
        i.setStatus(status(1, "To Do", StatusCategory.TO_DO));
        var parent = new Issue(); parent.setIssueId(900); i.setParentIssue(parent);
        return i;
    }
    // Sprint 100 thuộc project 10; trạng thái và ngày phục vụ các nhánh vòng đời.
    public static Sprint sprint(int id, int projectId, SprintState state) {
        var s = new Sprint(); s.setSprintId(id); s.setProject(project(projectId)); s.setSprintName("Sprint " + id);
        s.setState(state); s.setStartDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        s.setEndDate(LocalDateTime.of(2026, 1, 15, 0, 0)); return s;
    }
    // Role ID là khóa của user_roles, không phải user_id hoặc mã permission.
    public static UserRole role(int id, String name) {
        var r = new UserRole(); r.setRoleId(id); r.setRoleName(name); return r;
    }
}
