package com.example.jira.regression;

import com.example.jira.JiraApplication;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** HTTP thật trên loopback và schema smoke riêng trong DB test đã kiểm đích; không mock service/quyền/notification. */
@SpringBootTest(classes=JiraApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.default_schema=smoke","spring.datasource.hikari.schema=smoke",
        "app.jwt.secret="+SecurityRegressionTest.SECRET,"app.jwt.expiration=600000",
        "cloudinary.cloud-name=smoke-dummy","cloudinary.api-key=smoke-dummy","cloudinary.api-secret=smoke-dummy",
        "spring.mail.host=127.0.0.1","spring.mail.port=1","spring.mail.test-connection=false","spring.sql.init.mode=never"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SystemSmokeIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        // Gọi preflight dùng chung trước mọi DDL: local URL/data_directory phải đúng cluster mới do script tạo.
        PostgresRegressionIT.database(registry);
        String url=System.getProperty("test.pg.url",PostgresRegressionIT.POSTGRES.isRunning()?PostgresRegressionIT.POSTGRES.getJdbcUrl():"");
        String user=System.getProperty("test.pg.user",PostgresRegressionIT.POSTGRES.isRunning()?PostgresRegressionIT.POSTGRES.getUsername():"");
        String password=System.getProperty("test.pg.password",PostgresRegressionIT.POSTGRES.isRunning()?PostgresRegressionIT.POSTGRES.getPassword():"");
        try (var connection=java.sql.DriverManager.getConnection(url,user,password); var statement=connection.createStatement()) {
            try (var result=statement.executeQuery("select current_database()")) {
                if (!result.next() || !"jira_regression".equals(result.getString(1))) throw new IllegalStateException("Refusing unsafe smoke DB");
            }
            statement.execute("create schema if not exists smoke");
        }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMemberRepository members;
    @Autowired com.example.jira.service.NotificationService notifications;
    @Autowired UserRoleRepository roles;
    @Autowired UserRoleAssignmentRepository assignments;
    @Autowired IssueTypeRepository types;
    @Autowired PasswordEncoder encoder;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Một luồng nhiều bước kiểm kết quả đã ghi qua API thật, gồm cả security filter và quyền theo dữ liệu DB. */
    @Test void scrumWorkflowAndAccountSecurityWorkOverRealHttp() throws Exception {
        var admin=Fixtures.user(0); admin.setEmail("smoke-admin@test.invalid"); admin.setPasswordHash(encoder.encode("smoke-password")); admin=users.saveAndFlush(admin);
        var adminRole=roles.findByRoleName("SYSTEM_ADMIN").orElseThrow();
        var assignment=new UserRoleAssignment(); assignment.setId(new UserRoleAssignmentId(admin.getUserId(),adminRole.getRoleId())); assignment.setUser(admin); assignment.setRole(adminRole); assignments.saveAndFlush(assignment);
        String token=login(admin.getEmail(),"smoke-password");
        JsonNode project=call("POST","/api/projects/create",token,Map.of("projectName","Smoke","projectKey","SMOKE","description","HTTP test"),200); int projectId=project.path("projectId").asInt();
        assertThat(projectId).isPositive();
        JsonNode type=call("POST","/api/issue-types",token,Map.of("typeName","Smoke Task"),200);
        int typeId=type.path("issueTypeId").asInt(); assertThat(typeId).isPositive();
        call("POST","/api/projects/"+projectId+"/issue-types/"+typeId,token,null,200);
        JsonNode todo=call("POST","/api/issue-statuses",token,Map.of("statusName","Smoke Todo","statusCategory","TO_DO"),200);
        JsonNode done=call("POST","/api/issue-statuses",token,Map.of("statusName","Smoke Completed","statusCategory","DONE"),200);
        int todoId=todo.path("statusId").asInt(),doneId=done.path("statusId").asInt();
        call("POST","/api/workflows/rules",token,Map.of("fromStatusId",todoId,"toStatusId",doneId),201);
        JsonNode created=call("POST","/api/issues",token,Map.of("projectId",projectId,"issueTypeId",typeId,"summary","HTTP smoke issue","priority","HIGH"),201);
        int issueId=created.path("issueId").asInt(); assertThat(created.path("issueKey").asText()).isEqualTo("SMOKE-1"); assertThat(created.path("assigneeId").isNull()).isTrue();
        JsonNode estimated=call("PUT","/api/issues/"+issueId,token,Map.of("storyPoints",5),200);
        assertThat(estimated.path("version").asLong()).isGreaterThan(created.path("version").asLong());
        JsonNode backlog=call("GET","/api/projects/"+projectId+"/backlog",token,null,200); assertThat(backlog.size()).isEqualTo(1);
        call("PUT","/api/projects/"+projectId+"/backlog/order",token,Map.of("issueIds",List.of(issueId)),200);
        JsonNode search=call("GET","/api/projects/"+projectId+"/issues/search?keyword=HTTP&size=1",token,null,200); assertThat(search.path("totalItems").asInt()).isEqualTo(1);
        JsonNode sprint=call("POST","/api/sprints",token,Map.of("projectId",projectId,"sprintName","HTTP Sprint"),201); int sprintId=sprint.path("sprintId").asInt();
        call("POST","/api/sprints/"+sprintId+"/issues/"+issueId,token,null,200);
        call("PUT","/api/sprints/"+sprintId+"/start",token,null,200);
        JsonNode board=call("POST","/api/boards",token,Map.of("projectId",projectId,"boardName","HTTP Board","boardType","SCRUM"),201); int boardId=board.path("boardId").asInt();
        int columnId=board.path("columns").get(2).path("columnId").asInt();
        call("POST","/api/boards/columns/map-status",token,Map.of("columnId",columnId,"statusId",doneId),200);
        call("PATCH","/api/boards/columns/"+columnId,token,Map.of("wipLimit",1),204);
        JsonNode detail=call("GET","/api/issues/"+issueId,token,null,200);
        Map<String,Object> move=Map.of("columnId",columnId,"statusId",doneId,"position",0,"expectedVersion",detail.path("version").asLong());
        JsonNode moved=call("PUT","/api/boards/"+boardId+"/issues/"+issueId+"/move",token,move,200);
        assertThat(moved.path("issue").path("statusName").asText()).isEqualTo("Smoke Completed");
        call("PUT","/api/boards/"+boardId+"/issues/"+issueId+"/move",token,move,409);
        JsonNode cards=call("GET","/api/boards/"+boardId+"/issues",token,null,200); assertThat(cards.get(0).path("columnId").asInt()).isEqualTo(columnId);
        JsonNode comment=call("POST","/api/issues/"+issueId+"/comments",token,Map.of("commentText","Original comment"),201); int commentId=comment.path("commentId").asInt();
        JsonNode edited=call("PUT","/api/issues/"+issueId+"/comments/"+commentId,token,Map.of("commentText","Edited comment","expectedVersion",comment.path("version").asLong()),200);
        assertThat(edited.path("commentText").asText()).isEqualTo("Edited comment");
        call("DELETE","/api/issues/"+issueId+"/comments/"+commentId+"?expectedVersion="+edited.path("version").asLong(),token,null,204);
        call("PUT","/api/sprints/"+sprintId+"/complete",token,null,200);
        JsonNode velocity=call("GET","/api/projects/"+projectId+"/reports/velocity",token,null,200); assertThat(velocity.get(0).path("completedPoints").asInt()).isEqualTo(5);
        JsonNode burndown=call("GET","/api/sprints/"+sprintId+"/reports/burndown",token,null,200); assertThat(burndown.path("baselineAvailable").asBoolean()).isTrue(); assertThat(burndown.path("points").size()).isGreaterThanOrEqualTo(2);
        JsonNode audit=call("GET","/api/admin/audit-logs?entityType=COMMENT&entityId="+commentId,token,null,200); assertThat(audit.path("totalItems").asInt()).isEqualTo(3);
        assertThat(call("GET","/api/admin/overview",token,null,200).path("issues").asInt()).isEqualTo(1);
        seedVerifiedUser("smoke-user", "smoke-password");
        String regularToken=login("smoke-user@test.invalid", "smoke-password");
        call("GET","/api/admin/users",regularToken,null,403);call("GET","/api/user",regularToken,null,403);
        call("GET","/api/admin/users/"+admin.getUserId(),regularToken,null,403);
        JsonNode directory=call("GET","/api/admin/users?keyword=smoke&size=1",token,null,200);assertThat(directory.path("totalItems").asInt()).isEqualTo(2);assertThat(directory.path("items").size()).isEqualTo(1);
        JsonNode adminDetail=call("GET","/api/admin/users/"+admin.getUserId(),token,null,200);assertThat(adminDetail.has("passwordHash")).isFalse();assertThat(adminDetail.path("roles").get(0).path("roleName").asText()).isEqualTo("SYSTEM_ADMIN");
        call("GET","/api/admin/overview",regularToken,null,403); call("GET","/api/issues/"+issueId,regularToken,null,403);
        int regularId=users.findByEmail("smoke-user@test.invalid").orElseThrow().getUserId();
        int developerId=roles.findByRoleName("DEVELOPER").orElseThrow().getRoleId();
        seedMembership(projectId, regularId, developerId);
        notifications.sendNotification(regularId, null, admin.getUserId(), com.example.jira.enums.NotificationType.PROJECT_INVITED, "Smoke notification");
        assertThat(call("GET","/api/notifications/unread-count",regularToken,null,200).path("count").asInt()).isEqualTo(1);
        assertThat(call("PUT","/api/notifications/read-all",regularToken,null,200).path("updated").asInt()).isEqualTo(1);
        call("GET","/api/issues/"+issueId,regularToken,null,200);
        call("POST","/api/account/password",token,Map.of("oldPassword","smoke-password","newPassword","changed-password"),204);
        call("GET","/api/admin/overview",token,null,401);
        String newToken=login(admin.getEmail(),"changed-password"); call("GET","/api/admin/overview",newToken,null,200);
        JsonNode latest=call("GET","/api/issues/"+issueId,newToken,null,200);
        call("DELETE","/api/issues/"+issueId+"?expectedVersion="+latest.path("version").asLong(),newToken,null,204);
        call("GET","/api/issues/"+issueId,newToken,null,404);
        assertThat(call("GET","/api/projects/"+projectId+"/reports/velocity",newToken,null,200).get(0).path("completedPoints").asInt()).isEqualTo(5);
    }


    /** HTTP thật chứng minh user không global role tự tạo project và chỉ quản lý project mình sở hữu. */
    @Test void ordinaryUserOwnsProjectAndAdminRoleCrudIsScoped() throws Exception {
        String password="p1-regression-password";
        for(String name:List.of("p1-owner","p1-other")) seedVerifiedUser(name, password);
        String owner=login("p1-owner@test.invalid",password),other=login("p1-other@test.invalid",password);
        int ownerId=users.findByEmail("p1-owner@test.invalid").orElseThrow().getUserId();
        int otherId=users.findByEmail("p1-other@test.invalid").orElseThrow().getUserId();
        var context=call("GET","/api/account/context",owner,null,200);
        assertThat(context.path("systemAdmin").asBoolean()).isFalse();assertThat(context.path("globalPermissions").toString()).contains("CREATE_PROJECT").doesNotContain("DELETE_PROJECT");
        var created=call("POST","/api/projects/create",owner,Map.of("projectName","Owner project","projectKey","P1OWN"),200);int id=created.path("projectId").asInt();
        assertThat(created.path("memberCount").asInt()).isEqualTo(1);
        int pm=roles.findByRoleName("PROJECT_MANAGER").orElseThrow().getRoleId(),dev=roles.findByRoleName("DEVELOPER").orElseThrow().getRoleId();
        var member=call("GET","/api/projects/"+id+"/members",owner,null,200).get(0);
        assertThat(member.path("userId").asInt()).isEqualTo(ownerId);
        assertThat(call("GET","/api/account/context?projectId="+id,owner,null,200).path("projectPermissions").toString()).contains("DELETE_PROJECT");
        // Assignment global legacy không tạo quyền xem/sửa/xóa project không có membership.
        var legacy=new UserRoleAssignment();var developer=roles.findByRoleName("DEVELOPER").orElseThrow();
        legacy.setId(new UserRoleAssignmentId(otherId,developer.getRoleId()));legacy.setUser(users.findById(otherId).orElseThrow());legacy.setRole(developer);assignments.saveAndFlush(legacy);
        call("GET","/api/projects/"+id,other,null,403);
        call("PATCH","/api/projects/"+id,other,Map.of("projectName","Stolen"),403);
        call("DELETE","/api/projects/"+id,other,null,403);
        seedMembership(id, otherId, dev);
        call("GET","/api/projects/"+id,other,null,200);
        call("DELETE","/api/projects/"+id,owner,null,409);
        call("DELETE","/api/projects/"+id+"/members/"+otherId,owner,null,200);
        call("DELETE","/api/projects/"+id,owner,null,200);
        call("GET","/api/projects/"+id,owner,null,404);
        call("GET","/api/admin/roles",owner,null,403);
        // Admin cấu hình role dự án nhưng không được gán role đó toàn cục qua API cũ.
        var admin=Fixtures.user(0);admin.setEmail("p1-admin@test.invalid");admin.setPasswordHash(encoder.encode(password));admin=users.saveAndFlush(admin);
        var assignment=new UserRoleAssignment();var sys=roles.findByRoleName("SYSTEM_ADMIN").orElseThrow();
        assignment.setId(new UserRoleAssignmentId(admin.getUserId(),sys.getRoleId()));assignment.setUser(admin);assignment.setRole(sys);assignments.saveAndFlush(assignment);
        String token=login(admin.getEmail(),password);
        call("PUT","/api/admin/users/"+otherId+"/roles/"+pm,token,null,400);
        int roleId=call("POST","/api/user-role",token,Map.of("roleName"," p1_reviewer "),201).path("role").asInt();
        call("POST","/api/user-role",token,Map.of("roleName","P1_REVIEWER"),409);
        call("PUT","/api/user-role/"+pm,token,Map.of("roleName","RENAME"),409);
        call("DELETE","/api/user-role/"+dev,token,null,409);
        call("PUT","/api/user-role/"+roleId,token,Map.of("roleName","P1_REVIEWER_NEW"),200);
        assertThat(call("GET","/api/admin/roles",token,null,200).toString()).contains("P1_REVIEWER_NEW","projectAssignmentCount");
        call("DELETE","/api/user-role/"+roleId,token,null,204);
        call("DELETE","/api/user-role/"+roleId,token,null,404);
        call("POST","/api/user-role",other,Map.of("roleName","NO_ADMIN"),403);
    }

    /** Swagger phải sinh contract khi có advice/record DTO mới, không chỉ nạp được trang UI. */
    @Test void generatedOpenApiContainsNewRoutesAndBearerScheme() throws Exception {
        JsonNode spec=call("GET","/v3/api-docs",null,null,200);
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        for (String path:List.of("/api/projects/{projectId}/backlog","/api/boards/{boardId}/issues/{issueId}/move",
                "/api/projects/{projectId}/issues/search","/api/sprints/{sprintId}/reports/burndown","/api/account/password","/api/admin/audit-logs")) {
            assertThat(spec.path("paths").has(path)).as("OpenAPI path %s",path).isTrue();
        }
    }
    // Fixture riêng cho luồng board/account. Luồng tạo membership qua accept được kiểm trong suite email.
    private void seedMembership(int projectId, int userId, int roleId) {
        var member = new ProjectMember();
        member.setId(new ProjectMemberId(projectId, userId));
        member.setProject(projects.findById(projectId).orElseThrow());
        member.setUser(users.findById(userId).orElseThrow());
        member.setRole(roles.findById(roleId).orElseThrow());
        member.setAssignedAt(java.time.LocalDateTime.now());
        members.saveAndFlush(member);
    }

    private void seedVerifiedUser(String name, String password) {
        var user = Fixtures.user(0);
        user.setEmail(name + "@test.invalid");
        user.setUserName(name);
        user.setFullName(name);
        user.setEmailVerifiedAt(java.time.Instant.now());
        user.setPasswordHash(encoder.encode(password));
        users.saveAndFlush(user);
    }

    private String login(String email,String password) throws Exception {
        String token=call("POST","/api/auth/login",null,Map.of("email",email,"password",password),200).path("token").asText();
        assertThat(token.isBlank()).isFalse(); return token;
    }
    /** Chỉ gọi loopback của server test, không log body/token; status sai báo method/path và mã để tra server log. */
    private JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if (token!=null) request.header("Authorization","Bearer "+token);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s",method,path).isEqualTo(status);
        return response.body().isBlank()?json.nullNode():json.readTree(response.body());
    }
}
