package com.example.jira.regression;

import com.example.jira.config.DatabaseSeeder;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.security.jwt.JwtUtil;
import com.example.jira.dto.issue.CreateIssueRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import com.example.jira.service.NotificationService;
import com.example.jira.service.impl.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chạy trên container hoặc cluster PostgreSQL riêng do script tạo; không dùng DB jira của ứng dụng. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "app.jwt.secret=" + SecurityRegressionTest.SECRET, "app.jwt.expiration=60000"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostgresRegressionIT.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresRegressionIT {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jira_regression").withUsername("regression").withPassword("test-only");
    static boolean containerStarted;
    // Local mode do script tạo cluster mới. Kiểm đích trước Spring/Hibernate DDL, không fallback DB ứng dụng.
    static void verifyLocalDatabase(String url, String user, String password, String directory) {
        if (!"jdbc:postgresql://127.0.0.1:55432/jira_regression".equals(url) || !"regression".equals(user)
                || directory == null || !java.nio.file.Path.of(directory).toAbsolutePath().normalize().getParent()
                    .getFileName().toString().matches("test-postgres-[a-f0-9]{32}")) {
            throw new IllegalStateException("Refusing unsafe local test datasource");
        }
        try (var connection = java.sql.DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("select current_database(), current_user, current_setting('data_directory')")) {
            if (!rs.next() || !"jira_regression".equals(rs.getString(1)) || !"regression".equals(rs.getString(2))
                    || !java.nio.file.Path.of(rs.getString(3)).toRealPath().equals(java.nio.file.Path.of(directory).toRealPath())) {
                throw new IllegalStateException("Refusing database not owned by local test script");
            }
        } catch (Exception e) { throw new IllegalStateException("Local PostgreSQL pre-DDL verification failed", e); }
    }
    // Cấp thông tin kết nối từ container đang chạy, ghi đè datasource mặc định của ứng dụng.
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String local = System.getProperty("test.pg.url");
        if (local != null) {
            String user = System.getProperty("test.pg.user"); String password = System.getProperty("test.pg.password", "");
            verifyLocalDatabase(local, user, password, System.getProperty("test.pg.data"));
            r.add("spring.datasource.url", () -> local); r.add("spring.datasource.username", () -> user);
            r.add("spring.datasource.password", () -> password); return;
        }
        POSTGRES.start(); containerStarted = true;
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Configuration @EntityScan("com.example.jira.entity")
    @EnableJpaRepositories("com.example.jira.repository")
    @Import({BoardServiceImpl.class, SprintServiceImpl.class, IssueServiceImpl.class, RefreshTokenServiceImpl.class,
            AuthServiceImpl.class, BacklogService.class, BoardCardService.class, BoardWipPolicy.class, DatabaseSeeder.class})
    static class Config {
        // Cleanup container chạy khi context đóng; local cluster do script dừng sau khi Maven kết thúc.
        @Bean org.springframework.beans.factory.DisposableBean postgresCleanup() {
            return () -> { if (containerStarted) { POSTGRES.stop(); containerStarted = false; } };
        }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean JwtUtil jwt() {
            return new JwtUtil();
        }
    }
    @MockBean ProjectSecurity security;
    @MockBean NotificationService notifications;
    @MockBean AuthenticationManager authentication;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired IssueRepository issues;
    @Autowired IssueStatusRepository statuses;
    @Autowired BoardRepository boards;
    @Autowired BoardColumnRepository columns;
    @Autowired SprintRepository sprints;
    @Autowired SprintIssueRepository backlog;
    @Autowired RefreshTokenRepository tokens;
    @Autowired IssueTypeRepository types;
    @Autowired ProjectIssueTypeRepository projectTypes;
    @Autowired WorkflowRuleRepository workflow;
    @Autowired BoardServiceImpl boardService;
    @Autowired SprintServiceImpl sprintService;
    @Autowired IssueServiceImpl issueService;
    @Autowired RefreshTokenServiceImpl refreshService;
    @Autowired AuthServiceImpl authService;
    @Autowired BacklogService backlogService;
    @Autowired BoardCardService boardCardService;
    @Autowired BoardIssueRepository boardRanks;
    @Autowired BoardColumnStatusRepository boardMappings;
    @Autowired DatabaseSeeder seeder;
    User caller;
    Project project;

    // Làm sạch chỉ DB container riêng trước mỗi case; ghi fixture đã commit để hai thread cùng thấy.
    @BeforeEach void seedIsolatedDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("jira_regression");
        jdbc.execute("TRUNCATE users, projects, issue_types, issue_statuses, user_roles, permissions, workflow_rules RESTART IDENTITY CASCADE");
        caller = Fixtures.user(0); caller.setUserName("caller"); caller.setEmail("caller@test.invalid"); caller = users.saveAndFlush(caller);
        Fixtures.login(caller.getUserId()); project = Fixtures.project(0); project.setLead(caller); project = projects.saveAndFlush(project);
    }
    @AfterEach void clearIdentity() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    // Khóa duy nhất email phải do PostgreSQL bảo vệ, kể cả khi bỏ qua check exists ở service.
    @Test void duplicateEmailViolatesDatabaseConstraint() {
        var duplicate = Fixtures.user(0); duplicate.setEmail(caller.getEmail());
        assertThatThrownBy(() -> users.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(users.count()).isEqualTo(1);
    }
    // Migration được chạy lặp trên DB test và vẫn giữ ràng buộc chống trùng, không sửa dữ liệu cũ.
    @Test void uniquenessMigrationIsRepeatableAndRejectsDuplicateNumbers() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("scripts/migrations/001_issue_uniqueness.sql"));
        jdbc.execute(sql); jdbc.execute(sql);
        var original = persistedIssue();
        var duplicate = new Issue(); duplicate.setSummary("Duplicate number"); duplicate.setProject(project);
        duplicate.setIssueType(original.getIssueType()); duplicate.setStatus(original.getStatus());
        duplicate.setIssueNumber(original.getIssueNumber()); duplicate.setIssueKey("OTHER-KEY");
        assertThatThrownBy(() -> issues.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(issues.count()).isEqualTo(1);
    }
    // JPQL MAX phải lọc project, không lấy issue number của project khác.
    @Test void maxIssueNumberIsScopedByProject() {
        var i = persistedIssue(); i.setIssueNumber(7); issues.saveAndFlush(i);
        assertThat(issues.findMaxIssueNumberByProjectId(project.getProjectId())).contains(7);
        assertThat(issues.findMaxIssueNumberByProjectId(999)).isEmpty();
    }
    // Transaction thật phải rollback cột đầu khi cột sau thuộc board khác.
    @Test void columnReorderingRollsBackWhenLaterColumnIsInvalid() {
        var b1 = persistedBoard("one"); var b2 = persistedBoard("two");
        var c1 = persistedColumn(b1, "Todo", 1); var c2 = persistedColumn(b2, "Other", 2);
        when(security.hasPermission(project.getProjectId(), PermissionName.MANAGE_BOARD)).thenReturn(true);
        var a = new com.example.jira.dto.board.UpdateColumnOrderRequest(); a.setColumnId(c1.getColumnId()); a.setPosition(9);
        var b = new com.example.jira.dto.board.UpdateColumnOrderRequest(); b.setColumnId(c2.getColumnId()); b.setPosition(10);
        assertThatThrownBy(() -> boardService.updateColumnOrder(b1.getBoardId(), List.of(a, b))).hasMessageContaining("không thuộc");
        assertThat(columns.findById(c1.getColumnId()).orElseThrow().getPosition()).isEqualTo(1);
    }
    // Đóng sprint với đích không tồn tại phải rollback trạng thái CLOSED đã gán trước đó.
    @Test void invalidDestinationRollsBackSprintCompletion() {
        var s = new Sprint(); s.setProject(project); s.setState(SprintState.ACTIVE); s = sprints.saveAndFlush(s);
        when(security.hasPermission(project.getProjectId(), PermissionName.MANAGE_SPRINT)).thenReturn(true);
        int id = s.getSprintId();
        assertThatThrownBy(() -> sprintService.completeSprint(id, 999)).hasMessageContaining("đích");
        assertThat(sprints.findById(id).orElseThrow().getState()).isEqualTo(SprintState.ACTIVE);
    }
    // Cùng cặp sprint/issue lưu hai lần không tạo hai hàng do khóa chính ghép.
    @Test void duplicateBacklogLinkDoesNotCreateDuplicateRows() {
        var i = persistedIssue(); var s = new Sprint(); s.setProject(project); s.setState(SprintState.FUTURE); s = sprints.saveAndFlush(s);
        when(security.hasPermission(project.getProjectId(), PermissionName.MANAGE_SPRINT)).thenReturn(true);
        sprintService.addIssueToSprint(s.getSprintId(), i.getIssueId()); sprintService.addIssueToSprint(s.getSprintId(), i.getIssueId());
        assertThat(backlog.findBySprint_SprintId(s.getSprintId())).hasSize(1);
    }
    // Seeder chạy lại phải giữ số role, permission, assignment và admin không đổi.
    @Test void seederIsIdempotent() {
        seeder.run(); long usersBefore = users.count();
        var before = jdbc.queryForList("select role_id, permission_id from role_permissions order by role_id, permission_id");
        var permissionsBefore = jdbc.queryForList("select permission_id, permission_name from permissions order by permission_id");
        var assignmentsBefore = jdbc.queryForList("select user_id, role_id from user_role_assignments order by user_id, role_id");
        seeder.run(); assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(jdbc.queryForList("select role_id, permission_id from role_permissions order by role_id, permission_id")).isEqualTo(before);
        assertThat(jdbc.queryForList("select permission_id, permission_name from permissions order by permission_id")).isEqualTo(permissionsBefore);
        assertThat(jdbc.queryForList("select user_id, role_id from user_role_assignments order by user_id, role_id")).isEqualTo(assignmentsBefore);
        assertThat(jdbc.queryForObject("select count(*) from user_roles", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from users u join user_role_assignments a on a.user_id=u.user_id join user_roles r on r.role_id=a.role_id where u.email='admin@jira.com' and r.role_name='SYSTEM_ADMIN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_roles r join role_permissions rp on r.role_id=rp.role_id join permissions p on p.permission_id=rp.permission_id where r.role_name='SYSTEM_ADMIN' and p.permission_name='CREATE_PROJECT'", Integer.class)).isEqualTo(1);
    }
    // Hai transaction cùng đọc một version: chỉ một cập nhật được commit.
    @Test void optimisticLockRejectsOneConcurrentWriter() throws Exception {
        int id = persistedIssue().getIssueId(); var barrier = new CyclicBarrier(2);
        var outcomes = race(() -> {
            try {
                tx(() -> { var i = issues.findById(id).orElseThrow(); await(barrier); i.setSummary(Thread.currentThread().getName()); em.flush(); return null; });
                return "saved";
            } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) { return "conflict"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("saved", "conflict");
    }
    // BUG-011: verify và rotate đồng thời chỉ nên có một token con còn hiệu lực.
    @Test void concurrentRefreshMustNotForkActiveTokens() throws Exception {
        var old = refreshService.createRefreshToken(caller); var barrier = new CyclicBarrier(2);
        // Gọi đúng caller production, không bọc verify/rotate trong transaction khác hành vi thật.
        var outcomes = withPausedReads(refreshService, "refreshTokenRepository", tokens, RefreshTokenRepository.class,
                "findByTokenValueForUpdate", () -> race(() -> { await(barrier); return outcome(() -> authService.refreshToken(old.getTokenValue())); }));
        assertThat(outcomes.stream().filter(o -> o.failure() == null).count()).isEqualTo(1);
        for (var result : outcomes) {
            if (result.failure() != null) assertThat(result.failure()).hasMessageContaining("reuse detected");
        }
        assertThat(tokens.findByFamilyId(old.getFamilyId()).stream().filter(t -> !t.isRevoked()).count()).isLessThanOrEqualTo(1);
    }
    // BUG-012: hai request tạo issue thật cùng lúc phải thành công và sinh hai mã khác nhau.
    @Test void concurrentIssueCreationMustReturnDistinctKeys() throws Exception {
        var barrier = new CyclicBarrier(2);
        var type = new IssueType(); type.setTypeName("Task"); type = types.saveAndFlush(type);
        projectTypes.saveAndFlush(new ProjectIssueType(new ProjectIssueTypeId(project.getProjectId(), type.getIssueTypeId()), project, type, true));
        statuses.saveAndFlush(Fixtures.status(0, "To Do", StatusCategory.TO_DO));
        int typeId = type.getIssueTypeId(); int projectId = project.getProjectId(); int userId = caller.getUserId();
        when(security.hasPermission(projectId, PermissionName.CREATE_ISSUE)).thenReturn(true);
        var outcomes = withPausedReads(issueService, "issueRepository", issues, IssueRepository.class,
                "findMaxIssueNumberByProjectId", () -> race(() -> {
            Fixtures.login(userId);
            try {
                var r = new CreateIssueRequest(); r.setProjectId(projectId); r.setIssueTypeId(typeId); r.setSummary("Parallel task");
                await(barrier); return outcome(() -> issueService.createIssue(r));
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        }));
        assertThat(outcomes).allSatisfy(o -> assertThat(o.failure()).isNull());
        assertThat(issues.findByProject_ProjectId(projectId)).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from (select issue_key from issues group by issue_key having count(*) > 1) duplicates", Integer.class)).isZero();
    }
    // BUG-013: notification lỗi không được để status và lịch sử commit từng phần.
    @Test void failedNotificationMustNotLeavePartialStatusChange() {
        var issue = persistedIssue();
        var other = Fixtures.user(0); other.setEmail("assignee@test.invalid"); other = users.saveAndFlush(other);
        var from = statuses.saveAndFlush(Fixtures.status(0, "To Do", StatusCategory.TO_DO));
        var to = statuses.saveAndFlush(Fixtures.status(0, "Doing", StatusCategory.IN_PROGRESS));
        var rule = new WorkflowRule(); rule.setFromStatus(from); rule.setToStatus(to); workflow.saveAndFlush(rule);
        issue.setStatus(from); issue.setAssignee(other); issue = issues.saveAndFlush(issue);
        when(security.hasPermission(project.getProjectId(), PermissionName.EDIT_ISSUE)).thenReturn(true);
        doThrow(new IllegalStateException("notification unavailable")).when(notifications)
                .sendNotification(anyInt(), any(), any(), any(), anyString());
        int id = issue.getIssueId(); int toId = to.getStatusId();
        assertThatThrownBy(() -> issueService.changeIssueStatus(id, toId)).hasMessageContaining("notification unavailable");
        assertThat(jdbc.queryForObject("select status_id from issues where issue_id=?", Integer.class, id)).isEqualTo(from.getStatusId());
        assertThat(jdbc.queryForObject("select count(*) from issue_transitions where issue_id=?", Integer.class, id)).isZero();
    }
    // JPQL phải giữ status null, loại DONE theo category và chỉ loại link FUTURE/ACTIVE, không loại lịch sử CLOSED.
    @Test void productBacklogFiltersActualDatabaseScopeAndKeepsNullRelations() throws Exception {
        var sql = java.nio.file.Files.readString(java.nio.file.Path.of("scripts/migrations/002_backlog_order.sql"));
        jdbc.execute(sql); jdbc.execute(sql);
        var plain = persistedIssue();
        var completed = additionalIssue(2); completed.setStatus(statuses.saveAndFlush(Fixtures.status(0,"Completed",StatusCategory.DONE))); issues.saveAndFlush(completed);
        var open = additionalIssue(3); var historical = additionalIssue(4);
        var future = persistedSprint(SprintState.FUTURE); var closed = persistedSprint(SprintState.CLOSED);
        saveLink(future,open); saveLink(closed,historical);
        assertThat(backlogService.product(project.getProjectId())).extracting(com.example.jira.dto.issue.IssueResponse::getIssueId)
                .containsExactly(plain.getIssueId(),historical.getIssueId());
        assertThat(backlogService.sprint(closed.getSprintId())).extracting(com.example.jira.dto.issue.IssueResponse::getIssueId).containsExactly(historical.getIssueId());
    }
    // Rank thật được commit và đọc lại từ transaction khác; request stale không được đổi phần nào của thứ tự.
    @Test void backlogOrdersPersistAndStaleOrderDoesNotWrite() {
        var a = persistedIssue(); var b = additionalIssue(2); var sprint = persistedSprint(SprintState.FUTURE);
        int projectId = project.getProjectId(); when(security.hasPermission(projectId,PermissionName.EDIT_ISSUE)).thenReturn(true);
        backlogService.orderProduct(projectId,List.of(b.getIssueId(),a.getIssueId()));
        assertThat(backlogService.product(projectId)).extracting(com.example.jira.dto.issue.IssueResponse::getIssueId).containsExactly(b.getIssueId(),a.getIssueId());
        assertThatThrownBy(() -> backlogService.orderProduct(projectId,List.of(a.getIssueId())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(issues.findById(b.getIssueId()).orElseThrow().getBacklogPosition()).isZero();
        when(security.hasPermission(projectId,PermissionName.MANAGE_SPRINT)).thenReturn(true);
        sprintService.addIssueToSprint(sprint.getSprintId(),a.getIssueId()); sprintService.addIssueToSprint(sprint.getSprintId(),b.getIssueId());
        backlogService.orderSprint(sprint.getSprintId(),List.of(b.getIssueId(),a.getIssueId()));
        assertThat(backlogService.sprint(sprint.getSprintId())).extracting(com.example.jira.dto.issue.IssueResponse::getIssueId).containsExactly(b.getIssueId(),a.getIssueId());
        sprintService.removeIssueFromSprint(sprint.getSprintId(),b.getIssueId());
        assertThat(issues.findById(b.getIssueId()).orElseThrow().getBacklogPosition()).isNull();
        assertThat(backlogService.product(projectId)).extracting(com.example.jira.dto.issue.IssueResponse::getIssueId).containsExactly(b.getIssueId());
    }
    // Lỗi sau saveAll nhưng trước commit phải rollback tất cả rank đã gán, không chỉ dừng response.
    @Test void failedBacklogWriteRollsBackAllPositions() {
        var a = persistedIssue(); var b = additionalIssue(2); int projectId = project.getProjectId();
        when(security.hasPermission(projectId,PermissionName.EDIT_ISSUE)).thenReturn(true);
        var factory = new ProxyFactory(); factory.setInterfaces(IssueRepository.class); factory.setTarget(issues);
        factory.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("saveAll")) { em.flush(); throw new IllegalStateException("forced write failure"); }
            return result;
        });
        Object target = AopTestUtils.getUltimateTargetObject(backlogService); ReflectionTestUtils.setField(target,"issues",factory.getProxy());
        try {
            assertThatThrownBy(() -> backlogService.orderProduct(projectId,List.of(b.getIssueId(),a.getIssueId()))).hasMessageContaining("forced write failure");
            assertThat(issues.findById(a.getIssueId()).orElseThrow().getBacklogPosition()).isNull();
            assertThat(issues.findById(b.getIssueId()).orElseThrow().getBacklogPosition()).isNull();
        } finally { ReflectionTestUtils.setField(target,"issues",issues); }
    }
    // Reorder giữ snapshot dưới khóa; cả thêm sprint lẫn chuyển DONE phải chờ trước khi thay scope.
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void membershipAndStatusWaitForBacklogReorder(boolean changeStatus) throws Exception {
        var issue = persistedIssue(); var from = statuses.saveAndFlush(Fixtures.status(0,"Todo",StatusCategory.TO_DO));
        var done = statuses.saveAndFlush(Fixtures.status(0,"Completed",StatusCategory.DONE));
        issue.setStatus(from); issue = issues.saveAndFlush(issue);
        var rule = new WorkflowRule(); rule.setFromStatus(from); rule.setToStatus(done); workflow.saveAndFlush(rule);
        var sprint = persistedSprint(SprintState.FUTURE); int id = issue.getIssueId(), projectId = project.getProjectId(), callerId = caller.getUserId();
        when(security.hasPermission(eq(projectId),any())).thenReturn(true);
        var snapshot = new CountDownLatch(1); var release = new CountDownLatch(1); var writerAtLock = new CountDownLatch(1);
        var reader = new ProxyFactory(); reader.setInterfaces(IssueRepository.class); reader.setTarget(issues);
        reader.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("findProductBacklog")) {
                snapshot.countDown(); if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("reorder release timeout");
            }
            return result;
        });
        var writer = new ProxyFactory(); writer.setInterfaces(ProjectRepository.class); writer.setTarget(projects);
        writer.addAdvice((MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("findByIdForUpdate")) writerAtLock.countDown();
            return invocation.proceed();
        });
        Object readerTarget = AopTestUtils.getUltimateTargetObject(backlogService);
        Object writerTarget = AopTestUtils.getUltimateTargetObject(changeStatus ? issueService : sprintService);
        ReflectionTestUtils.setField(readerTarget,"issues",reader.getProxy()); ReflectionTestUtils.setField(writerTarget,"projectRepository",writer.getProxy());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var reorder = pool.submit(() -> backlogService.orderProduct(projectId,List.of(id)));
            assertThat(snapshot.await(10,TimeUnit.SECONDS)).isTrue();
            var mutation = pool.submit(() -> {
                Fixtures.login(callerId);
                try { if (changeStatus) issueService.changeIssueStatus(id,done.getStatusId()); else sprintService.addIssueToSprint(sprint.getSprintId(),id); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            assertThat(writerAtLock.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> mutation.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); reorder.get(10,TimeUnit.SECONDS); mutation.get(10,TimeUnit.SECONDS);
            assertThat(backlogService.product(projectId)).isEmpty();
        } finally {
            release.countDown(); pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS);
            ReflectionTestUtils.setField(readerTarget,"issues",issues); ReflectionTestUtils.setField(writerTarget,"projectRepository",projects);
        }
    }
    // Cùng version chỉ một move được commit; lượt sau đọc version mới dưới khóa và trả 409.
    @Test void concurrentMovesOfSameCardRejectStaleVersion() throws Exception {
        var fixture = moveFixture(0); var issue = fixture.issue();
        var results = race(() -> outcome(() -> authenticatedMove(fixture,issue)));
        assertOneSuccessAndConflict(results);
        assertThat(jdbc.queryForObject("select count(*) from issue_transitions where issue_id=?",Integer.class,issue.getIssueId())).isEqualTo(1);
        assertThat(issues.findById(issue.getIssueId()).orElseThrow().getVersion()).isGreaterThan(issue.getVersion());
        assertThat(boardCardService.cards(fixture.board().getBoardId())).singleElement().satisfies(card -> {
            assertThat(card.columnId()).isEqualTo(fixture.target().getColumnId());
            assertThat(card.version()).isEqualTo(issues.findById(issue.getIssueId()).orElseThrow().getVersion());
        });
    }
    // Hai card khác nhau tranh một chỗ WIP: lượt sau đếm lại dưới khóa, không vượt giới hạn.
    @Test void concurrentCardsCannotOverflowWip() throws Exception {
        var fixture = moveFixture(1); var other = additionalIssue(2); other.setStatus(fixture.issue().getStatus()); other = issues.saveAndFlush(other);
        List<Issue> candidates = List.of(fixture.issue(),other); var index = new java.util.concurrent.atomic.AtomicInteger();
        var results = race(() -> { var card = candidates.get(index.getAndIncrement()); return outcome(() -> authenticatedMove(fixture,card)); });
        assertOneSuccessAndConflict(results);
        assertThat(issues.countInBoardColumn(project.getProjectId(),fixture.target().getColumnId(),0)).isEqualTo(1);
        assertThat(boardRanks.count()).isEqualTo(1);
    }
    // Không thể đi vòng WIP bằng API đổi status; lỗi notification sau kiểm WIP cũng rollback rank/status/history.
    @Test void directStatusAndFailedMovePreserveWipAndAtomicity() {
        var fixture = moveFixture(1); var occupied = additionalIssue(2); occupied.setStatus(fixture.to()); occupied = issues.saveAndFlush(occupied);
        int id = fixture.issue().getIssueId();
        assertThatThrownBy(() -> issueService.changeIssueStatus(id,fixture.to().getStatusId()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(boardRanks.count()).isZero();
        occupied.setStatus(fixture.issue().getStatus()); issues.saveAndFlush(occupied);
        var recipient = Fixtures.user(0); recipient.setEmail("board-recipient@test.invalid"); recipient = users.saveAndFlush(recipient);
        var updated = issues.findById(id).orElseThrow(); updated.setAssignee(recipient); updated = issues.saveAndFlush(updated);
        doThrow(new IllegalStateException("notification unavailable")).when(notifications).sendNotification(anyInt(),any(),any(),any(),anyString());
        var request = moveRequest(fixture,updated);
        assertThatThrownBy(() -> boardCardService.move(fixture.board().getBoardId(),id,request)).hasMessageContaining("notification unavailable");
        assertThat(issues.findById(id).orElseThrow().getStatus().getStatusId()).isEqualTo(fixture.issue().getStatus().getStatusId());
        assertThat(boardRanks.count()).isZero(); assertThat(jdbc.queryForObject("select count(*) from issue_transitions",Integer.class)).isZero();
    }
    // Move chỉ đổi vị trí cũng tăng version; token cũ không được dùng để kéo lại lần nữa.
    @Test void sameColumnReorderIncrementsVersionAndMappingIsExclusive() {
        var fixture = moveFixture(0); var issue = fixture.issue(); var body = moveRequest(fixture,issue);
        var initial = boardCardService.move(fixture.board().getBoardId(),issue.getIssueId(),body);
        body.setExpectedVersion(initial.version());
        var reordered = boardCardService.move(fixture.board().getBoardId(),issue.getIssueId(),body);
        assertThat(reordered.version()).isGreaterThan(initial.version());
        assertThatThrownBy(() -> boardCardService.move(fixture.board().getBoardId(),issue.getIssueId(),body))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        var other = persistedColumn(fixture.board(),"Other",3);
        assertThatThrownBy(() -> boardService.mapStatusToColumn(other.getColumnId(),fixture.to().getStatusId()))
                .isInstanceOf(com.example.jira.exception.ConflictException.class);
        assertThat(boardMappings.findBoardMappings(fixture.board().getBoardId())).hasSize(1);
    }
    private void assertOneSuccessAndConflict(List<Outcome> results) {
        assertThat(results.stream().filter(result -> result.failure() == null)).hasSize(1);
        assertThat(results.stream().filter(result -> result.failure() != null).toList()).singleElement().satisfies(result ->
                assertThat(result.failure()).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(409)));
    }
    // Mỗi request chạy ở thread riêng cần danh tính riêng; không kế thừa SecurityContext của thread JUnit.
    private com.example.jira.dto.board.BoardCardResponse authenticatedMove(MoveFixture fixture,Issue issue) {
        Fixtures.login(caller.getUserId());
        try { return boardCardService.move(fixture.board().getBoardId(),issue.getIssueId(),moveRequest(fixture,issue)); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    private MoveFixture moveFixture(int limit) {
        var issue = persistedIssue(); var from = statuses.saveAndFlush(Fixtures.status(0,"Todo",StatusCategory.TO_DO));
        var to = statuses.saveAndFlush(Fixtures.status(0,"Doing",StatusCategory.IN_PROGRESS)); issue.setStatus(from); issue = issues.saveAndFlush(issue);
        var rule = new WorkflowRule(); rule.setFromStatus(from); rule.setToStatus(to); workflow.saveAndFlush(rule);
        var board = persistedBoard("Team"); var column = persistedColumn(board,"Doing",1); column.setWipLimit(limit); column = columns.saveAndFlush(column);
        var mapping = new BoardColumnStatus(); mapping.setId(new BoardColumnStatusId(column.getColumnId(),to.getStatusId())); mapping.setBoardColumn(column); mapping.setIssueStatus(to); boardMappings.saveAndFlush(mapping);
        when(security.hasPermission(eq(project.getProjectId()),any())).thenReturn(true);
        return new MoveFixture(board,column,to,issue);
    }
    private com.example.jira.dto.board.MoveBoardIssueRequest moveRequest(MoveFixture fixture,Issue issue) {
        var body = new com.example.jira.dto.board.MoveBoardIssueRequest(); body.setColumnId(fixture.target().getColumnId()); body.setStatusId(fixture.to().getStatusId());
        body.setPosition(0); body.setExpectedVersion(issue.getVersion()); return body;
    }
    private record MoveFixture(Board board,BoardColumn target,IssueStatus to,Issue issue) { }
    // Các helper chỉ tạo fixture đã commit trong DB test; không thay thuật toán production.
    private Sprint persistedSprint(SprintState state) {
        var sprint = new Sprint(); sprint.setProject(project); sprint.setState(state); return sprints.saveAndFlush(sprint);
    }
    private Issue additionalIssue(int number) {
        var issue = new Issue(); issue.setProject(project); issue.setSummary("Task " + number);
        issue.setIssueNumber(number); issue.setIssueKey("P0-" + number); return issues.saveAndFlush(issue);
    }
    private void saveLink(Sprint sprint, Issue issue) {
        var link = new SprintIssue(); link.setId(new SprintIssueId(sprint.getSprintId(),issue.getIssueId()));
        link.setSprint(sprint); link.setIssue(issue); backlog.saveAndFlush(link);
    }
    // Ghi issue tối thiểu hợp lệ; không cần assignee/cha vì các cột này cho phép null.
    private Issue persistedIssue() {
        var i = new Issue(); i.setProject(project); i.setReporter(caller); i.setSummary("Task");
        i.setIssueKey("P0-1"); i.setIssueNumber(1); return issues.saveAndFlush(i);
    }
    private Board persistedBoard(String name) { var b = new Board(); b.setProject(project); b.setBoardName(name); return boards.saveAndFlush(b); }
    private BoardColumn persistedColumn(Board b, String name, int position) {
        var c = new BoardColumn(); c.setBoard(b); c.setColumnName(name); c.setPosition(position); return columns.saveAndFlush(c);
    }
    // Mỗi lời gọi tạo transaction độc lập, không dùng transaction tự rollback của DataJpaTest.
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    // Giới hạn thời gian để deadlock/barrier hỏng làm test thất bại thay vì treo vô hạn.
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException("Concurrent test synchronization failed", e); }
    }
    private <T> List<T> race(Supplier<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try { var a = pool.submit(action::get); var b = pool.submit(action::get); return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)); }
        finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    // Thu kết quả từng request để tách lỗi nghiệp vụ dự kiến khỏi lỗi hạ tầng bất ngờ.
    private Outcome outcome(Supplier<?> action) {
        try { return new Outcome(action.get(), null); } catch (RuntimeException ex) { return new Outcome(null, ex); }
    }
    private record Outcome(Object value, RuntimeException failure) { }
    // Chèn khoảng dừng sau truy vấn thật để code hiện tại cùng đọc dữ liệu cũ trước khi ghi.
    // Chỉ thay dependency của bean test, giữ nguyên service và truy vấn PostgreSQL thật.
    // Chờ có giới hạn: nếu bản sửa dùng khóa DB để tuần tự hóa, lượt đầu vẫn được chạy tiếp.
    private <T> T withPausedReads(Object service, String field, Object repository, Class<?> repositoryType,
                                  String method, CheckedSupplier<T> action) throws Exception {
        var readers = new CountDownLatch(2); var factory = new ProxyFactory();
        factory.setInterfaces(repositoryType); factory.setTarget(repository);
        factory.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals(method)) {
                readers.countDown(); readers.await(3, TimeUnit.SECONDS);
            }
            return result;
        });
        Object target = AopTestUtils.getUltimateTargetObject(service);
        ReflectionTestUtils.setField(target, field, factory.getProxy());
        try { return action.get(); } finally { ReflectionTestUtils.setField(target, field, repository); }
    }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
}
