package com.example.jira.regression;

import com.example.jira.controller.*;
import com.example.jira.controller.auth.AuthController;
import com.example.jira.controller.user.UserController;
import com.example.jira.controller.issue.*;
import com.example.jira.controller.project.*;
import com.example.jira.config.CorsConfig;
import com.example.jira.security.*;
import com.example.jira.security.jwt.*;
import com.example.jira.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** Kiểm tra HTTP thật qua MVC và JWT filter; nghiệp vụ service được kiểm tra ở các lớp khác. */
@WebMvcTest(controllers = {AuthController.class, UserController.class, ProjectController.class,
        ProjectMemberController.class, ProjectIssueTypeController.class, IssueController.class,
        IssueCommentController.class, IssueAttachmentController.class, IssueTypeController.class,
        IssueStatusController.class, UserRoleController.class, PermissionController.class,
        RolePermissionController.class, WorkflowController.class, BoardController.class,
        SprintController.class, NotificationController.class, BacklogController.class, BoardCardController.class, IssueQueryController.class, ContainerManagementController.class, ScrumReportController.class, AccountManagementController.class, NotificationInboxController.class, AdminQueryController.class,AdminUserController.class}, properties = {
        "app.jwt.secret=" + SecurityRegressionTest.SECRET, "app.jwt.expiration=60000"})
@Import({SecurityConfig.class, JwtUtil.class, CorsConfig.class})
class ApiRegressionTest {
    @MockBean com.example.jira.service.impl.AdminRolePermissionQueryService rolePermissionQuery;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean AuthService auth;
    @MockBean UserService users;
    @MockBean ProjectService projects;
    @MockBean ProjectMemberService members;
    @MockBean ProjectIssueTypeService projectTypes;
    @MockBean IssueService issues;
    @MockBean IssueCommentService comments;
    @MockBean IssueAttachmentService attachments;
    @MockBean IssueTypeService types;
    @MockBean IssueStatusService statuses;
    @MockBean UserRoleService roles;
    @MockBean PermissionService permissions;
    @MockBean RolePermissionService grants;
    @MockBean WorkflowRuleService workflows;
    @MockBean BoardService boards;
    @MockBean SprintService sprints;
    @MockBean NotificationService notifications;
    @MockBean com.example.jira.service.impl.BacklogService backlog;
    @MockBean com.example.jira.service.impl.BoardCardService cards;
    @MockBean com.example.jira.service.impl.IssueQueryService queryIssues;
    @MockBean com.example.jira.service.impl.ContainerManagementService containers;
    @MockBean com.example.jira.service.impl.ScrumReportService reports;
    @MockBean com.example.jira.service.impl.AccountManagementService accounts;
    @MockBean com.example.jira.service.impl.NotificationInboxService inbox;
    @MockBean com.example.jira.service.impl.AdminQueryService adminQueries;
    @MockBean com.example.jira.service.impl.AdminUserQueryService adminUsers;

    /** ModelAttribute kiểm filter HTTP trước service; detail giữ nguyên target ID của request. */
    @Test void adminUserReadRoutesValidateFilters() throws Exception {
        mvc.perform(get("/api/admin/users?keyword=alice&enabled=false&page=2&size=10").header("Authorization",bearer())).andExpect(status().isOk());
        verify(adminUsers).search(argThat(r->r.getKeyword().equals("alice")&&Boolean.FALSE.equals(r.getEnabled())&&r.getPage()==2&&r.getSize()==10));
        mvc.perform(get("/api/admin/users/8").header("Authorization",bearer())).andExpect(status().isOk());verify(adminUsers).get(8);
        clearInvocations(adminUsers);
        for(String query:java.util.List.of("size=0","size=101","page=-1","enabled=wrong","keyword="+"a".repeat(101)))
            mvc.perform(get("/api/admin/users?"+query).header("Authorization",bearer())).andExpect(status().isBadRequest());
        verifyNoInteractions(adminUsers);
    }

    @Test void collaborationAndAdminRoutesPreserveOwnershipScopeAndVersion() throws Exception {
        mvc.perform(put("/api/issues/5/comments/7").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"commentText\":\"Changed\",\"expectedVersion\":3}")).andExpect(status().isOk());
        mvc.perform(delete("/api/issues/5/comments/7?expectedVersion=3").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(put("/api/notifications/read-all").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
        mvc.perform(delete("/api/notifications/7").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/audit-logs?entityType=ISSUE&entityId=5&actorId=2&page=1").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/overview").header("Authorization",bearer())).andExpect(status().isOk());
        verify(comments).updateComment(eq(5),eq(7),argThat(body -> body.expectedVersion()==3L)); verify(comments).deleteComment(5,7,3L);
        verify(inbox).unreadCount(); verify(inbox).readAll(); verify(inbox).delete(7);
        verify(adminQueries).audit(argThat(request -> request.getEntityId()==5 && request.getActorId()==2 && request.getPage()==1)); verify(adminQueries).overview();
    }

    // Controller chặn tên trắng và mô tả vượt cột varchar255 trước khi gọi service ghi project.
    @Test void projectValidationRejectsInvalidInputsBeforeService() throws Exception {
        mvc.perform(post("/api/projects/create").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectName\":\"  \",\"projectKey\":\"TEAM\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/projects/10").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectName\":\"Team\",\"description\":\""+"a".repeat(256)+"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(projects);
    }
    @Test void commentVersionAndAuditQueryValidationRejectInvalidBody() throws Exception {
        mvc.perform(put("/api/issues/5/comments/7").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"commentText\":\"Changed\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs?actorId=-1").header("Authorization",bearer())).andExpect(status().isBadRequest());
        verifyNoInteractions(comments,adminQueries);
    }

    @Test void oldJwtVersionIsRejectedAndNewVersionIsAccepted() throws Exception {
        var user=Fixtures.user(1); user.setAuthVersion(1);
        when(details.loadUserByUsername(user.getEmail())).thenReturn(UserPrincipal.fromUser(user));
        mvc.perform(get("/api/projects/getList").header("Authorization","Bearer " + jwt.generateToken(user.getEmail(),0))).andExpect(status().isUnauthorized());
        verifyNoInteractions(projects);
        mvc.perform(get("/api/projects/getList").header("Authorization","Bearer " + jwt.generateToken(user.getEmail(),1))).andExpect(status().isOk());
    }
    @Test void disabledAccountJwtCannotReachController() throws Exception {
        var user=Fixtures.user(1); user.setEnabled(false);
        when(details.loadUserByUsername(user.getEmail())).thenReturn(UserPrincipal.fromUser(user));
        mvc.perform(get("/api/projects/getList").header("Authorization","Bearer " + jwt.generateToken(user.getEmail()))).andExpect(status().isUnauthorized());
        verifyNoInteractions(projects);
    }
    @Test void accountRoutesPreserveIdentityAndTargetRoleIds() throws Exception {
        mvc.perform(patch("/api/account/profile").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Display\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/account/password").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users/2/roles").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/2/roles/7").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/users/2/roles/7").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/users/2/enabled").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isNoContent());
        allowMemberWrite(10);
        mvc.perform(put("/api/projects/10/members/2/role").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":7}")).andExpect(status().isNoContent());
        verify(accounts).profile(argThat(body -> "Display".equals(body.fullName()))); verify(accounts).password(any());
        verify(accounts).roles(2); verify(accounts).grantRole(2,7); verify(accounts).revokeRole(2,7); verify(accounts).enabled(2,false); verify(members).updateRole(10,2,7);
    }
    @Test void accountBodyValidationRejectsMissingValues() throws Exception {
        mvc.perform(post("/api/account/password").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/2/enabled").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/projects/10/members/2/role").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
    }

    @Test void reportRoutesUseSprintAndProjectScopes() throws Exception {
        mvc.perform(get("/api/sprints/100/reports/summary").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/sprints/100/reports/burndown").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/projects/10/reports/velocity?limit=5").header("Authorization",bearer())).andExpect(status().isOk());
        verify(reports).summary(100); verify(reports).burndown(100); verify(reports).velocity(10,5);
    }

    @Test void containerRoutesCallCorrectScope() throws Exception {
        mvc.perform(patch("/api/sprints/100").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"Ship\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/boards/50").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"boardName\":\"Team\"}")).andExpect(status().isNoContent());
        mvc.perform(patch("/api/boards/columns/60").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"wipLimit\":2}")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/sprints/100").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/boards/50").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/boards/columns/60").header("Authorization",bearer())).andExpect(status().isNoContent());
        verify(containers).updateSprint(eq(100),argThat(request -> "Ship".equals(request.getGoal())));
        verify(containers).updateBoard(eq(50),argThat(request -> "Team".equals(request.getBoardName())));
        verify(containers).updateColumn(eq(60),argThat(request -> request.getWipLimit() == 2));
        verify(containers).deleteSprint(100); verify(containers).deleteBoard(50); verify(containers).deleteColumn(60);
    }
    @Test void containerBodyValidationRejectsInvalidValues() throws Exception {
        mvc.perform(patch("/api/boards/50").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"boardName\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/boards/columns/60").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"wipLimit\":-1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(containers);
    }

    @ParameterizedTest @ValueSource(strings = {"page=-1","size=101","statusId=0","sprintId=-1"})
    void invalidIssueSearchQueryIsBadRequest(String query) throws Exception {
        mvc.perform(get("/api/projects/10/issues/search?" + query).header("Authorization",bearer())).andExpect(status().isBadRequest());
        verifyNoInteractions(queryIssues);
    }
    @Test void detailSearchAndDeleteRoutesPassScopeAndVersion() throws Exception {
        mvc.perform(get("/api/issues/5").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/projects/10/issues/search?keyword=task&page=1&size=5&unassigned=true").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(delete("/api/issues/5?expectedVersion=3").header("Authorization",bearer())).andExpect(status().isNoContent());
        verify(queryIssues).detail(5); verify(queryIssues).search(eq(10),argThat(request -> request.getPage() == 1 && request.getSize() == 5 && request.isUnassigned()));
        verify(queryIssues).delete(5,3L);
    }

    // Move bắt buộc version; thiếu hoặc âm không được đi vào nghiệp vụ.
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"columnId\":60,\"statusId\":1,\"position\":0}", "{\"columnId\":60,\"statusId\":1,\"position\":0,\"expectedVersion\":-1}"})
    void boardMoveRequiresVersion(String body) throws Exception {
        mvc.perform(put("/api/boards/50/issues/5/move").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(cards);
    }
    @Test void boardCardRoutesPreserveConcurrencyToken() throws Exception {
        mvc.perform(get("/api/boards/50/issues").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/boards/50/issues/5/move").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnId\":60,\"statusId\":1,\"position\":0,\"expectedVersion\":7}")).andExpect(status().isOk());
        verify(cards).cards(50); verify(cards).move(eq(50),eq(5),argThat(body -> body.getExpectedVersion() == 7L));
    }

    // Validation HTTP chặn body thiếu/null/ID không dương trước khi gọi service.
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"issueIds\":null}", "{\"issueIds\":[null]}", "{\"issueIds\":[0]}"})
    void invalidBacklogOrderIsBadRequest(String body) throws Exception {
        mvc.perform(put("/api/projects/10/backlog/order").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(backlog);
    }
    // Cả bốn route gọi đúng scope và giữ thứ tự JSON của service.
    @Test void backlogRoutesUseProjectAndSprintIds() throws Exception {
        mvc.perform(get("/api/projects/10/backlog").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/sprints/100/issues").header("Authorization", bearer())).andExpect(status().isOk());
        String body = "{\"issueIds\":[6,5]}";
        mvc.perform(put("/api/projects/10/backlog/order").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/sprints/100/issues/order").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        verify(backlog).product(10); verify(backlog).sprint(100);
        verify(backlog).orderProduct(10,java.util.List.of(6,5)); verify(backlog).orderSprint(100,java.util.List.of(6,5));
    }

    // Các endpoint nghiệp vụ phải chặn request chưa có danh tính trước khi gọi service.
    @ParameterizedTest @ValueSource(strings = {"/api/user", "/api/projects/getList", "/api/projects/10",
            "/api/projects/10/members", "/api/projects/10/issue-types", "/api/issues/project/10",
            "/api/issues/5/comments", "/api/issues/5/attachments", "/api/user-role", "/api/permissions",
            "/api/workflows/rules", "/api/boards/50", "/api/sprints/100", "/api/notifications",
            "/api/issue-types", "/api/issue-statuses"})
    void anonymousRequestsAreRejected(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().is4xxClientError());
        verifyNoInteractions(users, projects, members, projectTypes, issues, comments, attachments,
                roles, permissions, workflows, boards, sprints, notifications, types, statuses);
    }
    // JWT hợp lệ mở được route danh sách; service còn chịu trách nhiệm lọc quyền dự án.
    @ParameterizedTest @ValueSource(strings = {"/api/user", "/api/projects/getList", "/api/projects/10/members",
            "/api/projects/10/issue-types", "/api/issues/project/10", "/api/issues/5/comments",
            "/api/issues/5/attachments", "/api/user-role", "/api/permissions", "/api/workflows/rules",
            "/api/boards/project/10", "/api/sprints/project/10", "/api/notifications", "/api/issue-types", "/api/issue-statuses"})
    void authenticatedCollectionRoutesReturnJson(String url) throws Exception {
        mvc.perform(get(url).header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(content().json("[]"));
        // Đối chiếu route với lời gọi service để route trả nhầm danh sách cũng làm test đỏ.
        switch (url) {
            case "/api/user" -> verify(users).getAllUsers();
            case "/api/projects/getList" -> verify(projects).getProjects();
            case "/api/projects/10/members" -> verify(members).getMembers(10);
            case "/api/projects/10/issue-types" -> verify(projectTypes).getProjectIssueTypes(10);
            case "/api/issues/project/10" -> verify(issues).getIssuesByProject(10);
            case "/api/issues/5/comments" -> verify(comments).getCommentsByIssue(5);
            case "/api/issues/5/attachments" -> verify(attachments).getAttachments(5);
            case "/api/user-role" -> verify(roles).getAllUserRoles();
            case "/api/permissions" -> verify(permissions).getAllPermissions();
            case "/api/workflows/rules" -> verify(workflows).getAllRules();
            case "/api/boards/project/10" -> verify(boards).getBoardsByProject(10);
            case "/api/sprints/project/10" -> verify(sprints).getSprintsByProject(10);
            case "/api/notifications" -> verify(notifications).getNotificationsForCurrentUser();
            case "/api/issue-types" -> verify(types).getAllIssueTypes();
            case "/api/issue-statuses" -> verify(statuses).getAllStatuses();
            default -> throw new AssertionError("Route chưa có assertion service: " + url);
        }
    }
    // Validation controller phải chặn sprint thiếu tên trước service.
    @Test void blankSprintNameReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/sprints").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":10,\"sprintName\":\"\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(sprints);
    }
    // Không truyền projectId phải bị validation từ chối.
    @Test void missingSprintProjectReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/sprints").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sprintName\":\"Sprint 1\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(sprints);
    }
    // Body JSON sai cú pháp trả 400, không gọi nghiệp vụ.
    @Test void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/issues").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{broken")) .andExpect(status().isBadRequest()); verifyNoInteractions(issues);
    }
    // Param assigneeId không phải số bị chặn ở binding.
    @Test void invalidAssigneeIdReturnsBadRequest() throws Exception {
        mvc.perform(put("/api/issues/5/assign").header("Authorization", bearer()).param("assigneeId", "abc"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(issues);
    }
    // Mapping request gán việc phải giữ cả issue ID lẫn user nhận việc.
    @Test void assignRoutePassesBothIds() throws Exception {
        mvc.perform(put("/api/issues/5/assign").header("Authorization", bearer()).param("assigneeId", "2"))
                .andExpect(status().isOk()); verify(issues).assignIssue(5, 2);
    }
    // Gỡ người nhận dùng null có chủ đích, không thay bằng user ID 0.
    @Test void unassignRoutePassesNull() throws Exception {
        mvc.perform(put("/api/issues/5/assign").header("Authorization", bearer()))
                .andExpect(status().isOk()); verify(issues).assignIssue(5, null);
    }
    // Hoàn tất sprint truyền sprint đích riêng với sprint nguồn.
    @Test void completeSprintRoutePreservesTargetId() throws Exception {
        mvc.perform(put("/api/sprints/100/complete").header("Authorization", bearer()).param("moveToSprintId", "101"))
                .andExpect(status().isOk()); verify(sprints).completeSprint(100, 101);
    }
    // Các mutation kiểm HTTP status và ID/nội dung xuống service; nghiệp vụ kiểm riêng ở unit/integration.
    @ParameterizedTest(name = "{0} {1}") @MethodSource("mutations")
    void mutationRoutesPreserveRequest(String method, String url, String json, int expected, String operation) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), url).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(expected));
        switch (operation) {
            case "project.create" -> verify(projects).createProject(argThat(r -> "TEAM".equals(r.getProjectKey())));
            case "project.update" -> verify(projects).updateProject(eq(10), argThat(r -> "Changed".equals(r.getProjectName())));
            case "project.delete" -> verify(projects).deleteProject(10);
            case "issue.create" -> verify(issues).createIssue(argThat(r -> r.getProjectId() == 10 && "Task".equals(r.getSummary())));
            case "issue.update" -> verify(issues).updateIssue(eq(5), argThat(r -> "Changed".equals(r.getSummary())));
            case "issue.status" -> verify(issues).changeIssueStatus(5, 3);
            case "comment.create" -> verify(comments).addComment(eq(5), argThat(r -> "Review".equals(r.getCommentText())));
            case "attachment.delete" -> verify(attachments).deleteAttachment(8);
            case "role.create" -> verify(roles).createRole(argThat(r -> "REVIEWER".equals(r.getRoleName())));
            case "role.update" -> verify(roles).updateRole(eq(7), argThat(r -> "REVIEWER".equals(r.getRoleName())));
            case "role.delete" -> verify(roles).deleteRole(7);
            case "permission.create" -> verify(permissions).createPermission(argThat(r -> "EDIT_ISSUE".equals(r.getPermissionName())));
            case "permission.update" -> verify(permissions).updatePermission(eq(8), argThat(r -> "EDIT_ISSUE".equals(r.getPermissionName())));
            case "permission.delete" -> verify(permissions).deletePermission(8);
            case "grant.add" -> verify(grants).addPermissionToRole(7, 8);
            case "grant.remove" -> verify(grants).removePermissionToRole(7, 8);
            case "type.create" -> verify(types).createIssueType(argThat(r -> "Bug".equals(r.getTypeName())));
            case "type.update" -> verify(types).updateIssueType(eq(7), argThat(r -> "Bug".equals(r.getTypeName())));
            case "type.delete" -> verify(types).deleteIssueType(7);
            case "status.create" -> verify(statuses).createStatus(argThat(r -> "Done".equals(r.getStatusName())));
            case "status.update" -> verify(statuses).updateStatus(eq(3), argThat(r -> "Done".equals(r.getStatusName())));
            case "status.delete" -> verify(statuses).deleteStatus(3);
            case "workflow.create" -> verify(workflows).createRule(argThat(r -> r.getFromStatusId() == 1 && r.getToStatusId() == 3));
            case "workflow.delete" -> verify(workflows).deleteRule(9);
            case "projectType.add" -> verify(projectTypes).assignIssueType(10, 7, true);
            case "projectType.default" -> verify(projectTypes).setDefaultIssueType(10, 7);
            case "projectType.remove" -> verify(projectTypes).removeIssueType(10, 7);
            case "sprint.create" -> verify(sprints).createSprint(argThat(r -> r.getProjectId() == 10 && "Sprint 1".equals(r.getSprintName())));
            case "sprint.add" -> verify(sprints).addIssueToSprint(100, 5);
            case "sprint.remove" -> verify(sprints).removeIssueFromSprint(100, 5);
            case "sprint.start" -> verify(sprints).startSprint(100);
            case "board.create" -> verify(boards).createBoard(argThat(r -> r.getProjectId() == 10 && "Team".equals(r.getBoardName())));
            case "column.create" -> verify(boards).addColumn(argThat(r -> r.getBoardId() == 50 && "Review".equals(r.getColumnName())));
            case "column.map" -> verify(boards).mapStatusToColumn(60, 3);
            case "column.unmap" -> verify(boards).unmapStatusFromColumn(60, 3);
            case "notification.read" -> verify(notifications).markAsRead(8);
            default -> throw new AssertionError("Mutation chưa có assertion: " + operation);
        }
    }
    // Mỗi dòng là một route đang có, không thêm route cho chức năng chưa triển khai.
    static Stream<Arguments> mutations() {
        return Stream.of(
            Arguments.of("POST", "/api/projects/create", "{\"projectKey\":\"TEAM\",\"projectName\":\"Team\"}", 200, "project.create"),
            Arguments.of("PATCH", "/api/projects/10", "{\"projectName\":\"Changed\"}", 200, "project.update"),
            Arguments.of("DELETE", "/api/projects/10", "", 200, "project.delete"),
            Arguments.of("POST", "/api/issues", "{\"projectId\":10,\"summary\":\"Task\"}", 201, "issue.create"),
            Arguments.of("PUT", "/api/issues/5", "{\"summary\":\"Changed\"}", 200, "issue.update"),
            Arguments.of("PUT", "/api/issues/5/status?statusId=3", "", 200, "issue.status"),
            Arguments.of("POST", "/api/issues/5/comments", "{\"commentText\":\"Review\"}", 201, "comment.create"),
            Arguments.of("DELETE", "/api/issues/attachments/8", "", 200, "attachment.delete"),
            Arguments.of("POST", "/api/user-role", "{\"roleName\":\"REVIEWER\"}", 201, "role.create"),
            Arguments.of("PUT", "/api/user-role/7", "{\"roleName\":\"REVIEWER\"}", 200, "role.update"),
            Arguments.of("DELETE", "/api/user-role/7", "", 204, "role.delete"),
            Arguments.of("POST", "/api/permissions", "{\"permissionName\":\"EDIT_ISSUE\"}", 201, "permission.create"),
            Arguments.of("PUT", "/api/permissions/8", "{\"permissionName\":\"EDIT_ISSUE\"}", 200, "permission.update"),
            Arguments.of("DELETE", "/api/permissions/8", "", 204, "permission.delete"),
            Arguments.of("POST", "/api/roles/7/permissions?permissionId=8", "", 200, "grant.add"),
            Arguments.of("DELETE", "/api/roles/7/permissions?permissionId=8", "", 204, "grant.remove"),
            Arguments.of("POST", "/api/issue-types", "{\"typeName\":\"Bug\"}", 200, "type.create"),
            Arguments.of("PUT", "/api/issue-types/7", "{\"typeName\":\"Bug\"}", 200, "type.update"),
            Arguments.of("DELETE", "/api/issue-types/7", "", 204, "type.delete"),
            Arguments.of("POST", "/api/issue-statuses", "{\"statusName\":\"Done\",\"statusCategory\":\"DONE\"}", 200, "status.create"),
            Arguments.of("PUT", "/api/issue-statuses/3", "{\"statusName\":\"Done\",\"statusCategory\":\"DONE\"}", 200, "status.update"),
            Arguments.of("DELETE", "/api/issue-statuses/3", "", 204, "status.delete"),
            Arguments.of("POST", "/api/workflows/rules", "{\"fromStatusId\":1,\"toStatusId\":3}", 201, "workflow.create"),
            Arguments.of("DELETE", "/api/workflows/rules/9", "", 204, "workflow.delete"),
            Arguments.of("POST", "/api/projects/10/issue-types/7?isDefault=true", "", 200, "projectType.add"),
            Arguments.of("PUT", "/api/projects/10/issue-types/7/default", "", 200, "projectType.default"),
            Arguments.of("DELETE", "/api/projects/10/issue-types/7", "", 204, "projectType.remove"),
            Arguments.of("POST", "/api/sprints", "{\"projectId\":10,\"sprintName\":\"Sprint 1\"}", 201, "sprint.create"),
            Arguments.of("POST", "/api/sprints/100/issues/5", "", 200, "sprint.add"),
            Arguments.of("DELETE", "/api/sprints/100/issues/5", "", 200, "sprint.remove"),
            Arguments.of("PUT", "/api/sprints/100/start", "", 200, "sprint.start"),
            Arguments.of("POST", "/api/boards", "{\"projectId\":10,\"boardName\":\"Team\",\"boardType\":\"SCRUM\"}", 201, "board.create"),
            Arguments.of("POST", "/api/boards/columns", "{\"boardId\":50,\"columnName\":\"Review\",\"position\":3}", 201, "column.create"),
            Arguments.of("POST", "/api/boards/columns/map-status", "{\"columnId\":60,\"statusId\":3}", 200, "column.map"),
            Arguments.of("DELETE", "/api/boards/columns/map-status?columnId=60&statusId=3", "", 204, "column.unmap"),
            Arguments.of("PUT", "/api/notifications/8/read", "", 200, "notification.read")
        );
    }
    // BUG-010: JWT sai định dạng phải trả 401, không làm exception thoát filter.
    @Test void malformedBearerReturnsUnauthorized() {
        assertThatCode(() -> mvc.perform(get("/api/user").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())).doesNotThrowAnyException();
    }
    // JWT hết hạn phải được xử lý như lỗi xác thực ngay tại HTTP filter.
    @Test void expiredBearerReturnsUnauthorized() {
        var expired = io.jsonwebtoken.Jwts.builder().setSubject("user1@test.invalid").setExpiration(new java.util.Date(0))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SecurityRegressionTest.SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        assertThatCode(() -> mvc.perform(get("/api/user").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())).doesNotThrowAnyException();
    }
    // JWT ký bằng khóa khác phải trả 401, không để exception parser thoát ra ngoài.
    @Test void wrongSignatureReturnsUnauthorized() {
        var bad = io.jsonwebtoken.Jwts.builder().setSubject("user1@test.invalid")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor((SecurityRegressionTest.SECRET + "other").getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        assertThatCode(() -> mvc.perform(get("/api/user").header("Authorization", "Bearer " + bad))
                .andExpect(status().isUnauthorized())).doesNotThrowAnyException();
    }
    // Endpoint đăng nhập public trả cặp token từ service, không cần JWT có trước.
    @Test void loginReturnsAccessAndRefreshTokens() throws Exception {
        when(auth.login(any())).thenReturn(new com.example.jira.dto.auth.AuthResponseDTO("access", "refresh"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user1@test.invalid\",\"password\":\"sample\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
        verify(auth).login(argThat(r -> "user1@test.invalid".equals(r.getEmail()) && "sample".equals(r.getPassword())));
    }
    // Đăng ký public chuyển đúng email xuống service và trả refresh token.
    @Test void registerBindsAccountData() throws Exception {
        when(auth.register(any())).thenReturn(new com.example.jira.dto.auth.AuthResponseDTO("access", "refresh"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@test.invalid\",\"password\":\"sample\",\"userName\":\"new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value("refresh"));
        verify(auth).register(argThat(r -> "new@test.invalid".equals(r.getEmail()) && "new".equals(r.getUserName())));
    }
    // Refresh và logout lấy token từ body, không nhầm với access token trong header.
    @Test void refreshAndLogoutUseBodyRefreshToken() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk()); verify(auth).refreshToken("old");
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk()); verify(auth).logout("old");
    }
    // /me phải dùng email trong Authentication và giữ wrapper JSON user cho FE.
    @Test void meReturnsAuthenticatedUserWrapper() throws Exception {
        when(auth.getCurrentUser("user1@test.invalid")).thenReturn(new com.example.jira.dto.user.UserSummary(1, "user1@test.invalid", "user1"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("user1@test.invalid"));
        verify(auth).getCurrentUser("user1@test.invalid");
    }
    // /me chưa đăng nhập phải trả 401, không gọi hàm đọc user.
    @Test void anonymousMeReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()); verifyNoInteractions(auth);
    }
    // Notification có dữ liệu phải serialize đúng người nhận và trạng thái đọc.
    @Test void notificationResponsePreservesFields() throws Exception {
        var n = new com.example.jira.dto.notification.NotificationResponse(); n.setNotificationId(8); n.setUserId(1); n.setRead(false); n.setMessage("Assigned");
        when(notifications.getNotificationsForCurrentUser()).thenReturn(java.util.List.of(n));
        mvc.perform(get("/api/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].notificationId").value(8))
                .andExpect(jsonPath("$[0].userId").value(1)).andExpect(jsonPath("$[0].read").value(false));
    }
    // Dùng parser và JWT filter thật; chỉ mock phần tra user của ứng dụng.
    // Route thêm/gỡ thành viên giữ tách biệt ID người gọi và ID thành viên đích.
    @Test void memberMutationRoutesUseCorrectParticipants() throws Exception {
        allowMemberWrite(10);
        mvc.perform(post("/api/projects/10/members").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":2,\"roleId\":7}")) .andExpect(status().isOk());
        verify(members).addMember(eq(10), argThat(r -> r.getUserId() == 2 && r.getRoleId() == 7));
        mvc.perform(delete("/api/projects/10/members/2").header("Authorization", bearer())).andExpect(status().isOk());
        verify(members).removeMember(10, 2);
    }

    @Test void memberCandidateSearchAcceptsOneEmailAndRejectsInvalidInput() throws Exception {
        var result = new com.example.jira.dto.project.MemberCandidate(2, "user2", null);
        when(members.findCandidate(10, "user2@test.invalid")).thenReturn(java.util.List.of(result));
        mvc.perform(post("/api/projects/10/members/candidates/search").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\" user2@test.invalid \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(2)).andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(post("/api/projects/10/members/candidates/search").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user2\"}")).andExpect(status().isBadRequest());
        verify(members).findCandidate(10, "user2@test.invalid");
        verifyNoMoreInteractions(members);
    }

    private void allowMemberWrite(int projectId) {
        when(auth.getCurrentUser()).thenReturn(new com.example.jira.dto.user.UserSummary(1, "user1@test.invalid", "user1"));
        when(permissions.checkPermission(eq(1), anyString(), eq(projectId))).thenReturn(true);
    }
    // Multipart giữ nội dung file và issue ID, không lẫn với attachment ID.
    @Test void multipartUploadBindsFileToIssue() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "proof.txt", "text/plain", new byte[]{1, 2});
        mvc.perform(multipart("/api/issues/5/attachments").file(file).header("Authorization", bearer()))
                .andExpect(status().isCreated());
        verify(attachments).uploadAttachment(eq(5), argThat(f -> "proof.txt".equals(f.getOriginalFilename()) && f.getSize() == 2));
    }
    // Reorder giữ board ID và danh sách columnId/position từ body.
    @Test void reorderColumnRoutePreservesPositions() throws Exception {
        mvc.perform(put("/api/boards/50/columns/order").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"columnId\":60,\"position\":3}]")) .andExpect(status().isOk());
        verify(boards).updateColumnOrder(eq(50), argThat(r -> r.size() == 1 && r.get(0).getColumnId() == 60 && r.get(0).getPosition() == 3));
    }
    // Query unreadOnly phải chọn nhánh chưa đọc thay vì trả toàn bộ thông báo.
    @Test void unreadOnlyUsesUnreadService() throws Exception {
        mvc.perform(get("/api/notifications?unreadOnly=true").header("Authorization", bearer())).andExpect(status().isOk());
        verify(notifications).getUnreadNotificationsForCurrentUser(); verify(notifications, never()).getNotificationsForCurrentUser();
    }
    // Trạng thái nguồn của workflow lấy từ query, không dùng mặc định ngầm.
    @Test void allowedTransitionRouteUsesSourceId() throws Exception {
        mvc.perform(get("/api/workflows/allowed-transitions?currentStatusId=1").header("Authorization", bearer())).andExpect(status().isOk());
        verify(workflows).getAllowedNextStatuses(1);
    }
    // Các route chi tiết dùng đúng ID trong path.
    @Test void detailRoutesPreserveResourceIds() throws Exception {
        mvc.perform(get("/api/projects/10").header("Authorization", bearer())).andExpect(status().isOk()); verify(projects).getProject(10);
        mvc.perform(get("/api/boards/50").header("Authorization", bearer())).andExpect(status().isOk()); verify(boards).getBoard(50);
        mvc.perform(get("/api/sprints/100").header("Authorization", bearer())).andExpect(status().isOk()); verify(sprints).getSprint(100);
    }
    // Dùng parser và JWT filter thật; chỉ mock phần tra user của ứng dụng.
    private String bearer() {
        when(details.loadUserByUsername("user1@test.invalid")).thenReturn(UserPrincipal.fromUser(Fixtures.user(1)));
        return "Bearer " + jwt.generateToken("user1@test.invalid");
    }
}
