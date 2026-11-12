package com.example.jira.regression;

import com.example.jira.dto.project.*;
import com.example.jira.config.PasswordConfig;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.mapper.ProjectMapper;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.service.*;
import com.example.jira.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectRegressionTest {
    @BeforeEach void identifyCaller() { Fixtures.login(1); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    @Nested @ExtendWith(MockitoExtension.class)
    class Projects {
        @Mock ProjectRepository projects;
        @Mock UserRepository users;
        @Spy ProjectMapper mapper = new ProjectMapper();
        @Mock UserRoleAssignmentRepository assignments;
        @Mock GlobalSecurity global;
        @Mock ProjectSecurity security;
        @Mock AuditLogService audit;
        @Mock ProjectStatsRepository stats;
        @InjectMocks ProjectServiceImpl service;
        // Tạo project lấy lead từ user đang đăng nhập, không từ request của client.
        @Test void createUsesAuthenticatedLead() {
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(global.hasPermission(PermissionName.CREATE_PROJECT)).thenReturn(true);
            var r = new CreateProjectRequest(); r.setProjectKey("TEAM"); r.setProjectName("Team");
            assertThat(service.createProject(r).getProjectKey()).isEqualTo("TEAM");
            verify(projects).save(argThat(p -> p.getLead().getUserId() == 1 && p.getCreatedAt() != null));
        }
        // Project key trùng phải dừng trước save.
        @Test void duplicateProjectKeyDoesNotWrite() {
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(global.hasPermission(PermissionName.CREATE_PROJECT)).thenReturn(true);
            when(projects.existsByProjectKey("TEAM")).thenReturn(true);
            var r = new CreateProjectRequest(); r.setProjectKey("TEAM"); r.setProjectName("Team");
            assertThatThrownBy(() -> service.createProject(r)).hasMessageContaining("tồn tại"); verify(projects, never()).save(any());
        }
        // Không có quyền tạo không được lưu project.
        @Test void deniedCreateDoesNotWrite() {
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            assertThatThrownBy(() -> service.createProject(new CreateProjectRequest())).hasMessageContaining("quyền");
            verify(projects, never()).save(any());
        }
        // Cập nhật hợp lệ chỉ đổi thông tin mô tả, giữ mã project và lead.
        @Test void updatePreservesProjectIdentity() {
            var p = Fixtures.project(10); when(projects.findById(10)).thenReturn(Optional.of(p));
            when(security.hasPermission(10, PermissionName.UPDATE_PROJECT)).thenReturn(true);
            var r = new UpdateProjectRequest(); r.setProjectName("Changed"); r.setDescription("New description");
            service.updateProject(10, r); assertThat(p.getProjectName()).isEqualTo("Changed");
            assertThat(p.getProjectKey()).isEqualTo("P10"); assertThat(p.getLead().getUserId()).isEqualTo(99);
        }
        // Xóa project cần quyền toàn cục; thiếu quyền không ghi audit giả.
        @Test void deniedDeleteDoesNotWrite() {
            assertThatThrownBy(() -> service.deleteProject(10)).hasMessageContaining("quyền");
            verify(projects, never()).delete(any()); verifyNoInteractions(audit);
        }
        // Xóa thành công ghi audit đúng project ID.
        @Test void deletionRecordsAudit() {
            var p = Fixtures.project(10); when(projects.findById(10)).thenReturn(Optional.of(p));
            when(global.hasPermission(PermissionName.DELETE_PROJECT)).thenReturn(true);
            service.deleteProject(10); verify(projects).delete(p);
            verify(audit).logAction(eq("DELETE"), eq(EntityType.PROJECT), eq(10), contains("P10"));
        }
        // BUG-007: người ngoài không được đọc chi tiết project bằng ID đoán được.
        @Test void outsiderCannotReadProject() {
            when(projects.findById(10)).thenReturn(Optional.of(Fixtures.project(10)));
            assertThatThrownBy(() -> service.getProject(10)).isInstanceOf(RuntimeException.class);
        }
        // Chỉ gửi ID được VIEW_PROJECT tới thống kê; trường count/tên sprint lấy đúng response của repository.
        @Test void listStatisticsOnlyReadsVisibleProjects() {
            when(projects.findAll()).thenReturn(List.of(Fixtures.project(10), Fixtures.project(20)));
            when(security.hasPermission(10, PermissionName.VIEW_PROJECT)).thenReturn(true);
            when(stats.read(List.of(10))).thenReturn(Map.of(10, new ProjectStatsRepository.Counts(8,3,2,"Sprint 2")));
            var result = service.getProjects(); assertThat(result).hasSize(1);
            assertThat(result.get(0).getIssueCount()).isEqualTo(8); assertThat(result.get(0).getMemberCount()).isEqualTo(3);
            assertThat(result.get(0).getSprintCount()).isEqualTo(2); assertThat(result.get(0).getActiveSprintName()).isEqualTo("Sprint 2");
            verify(stats).read(List.of(10));
        }
        // Tên rỗng bị từ chối trước lưu, không dựa vào NOT NULL vì chuỗi trắng vẫn được DB chấp nhận.
        @Test void createRejectsBlankNameBeforeSave() {
            when(users.findById(1)).thenReturn(Optional.of(Fixtures.user(1)));
            when(global.hasPermission(PermissionName.CREATE_PROJECT)).thenReturn(true);
            var r = new CreateProjectRequest(); r.setProjectKey("TEAM"); r.setProjectName("  ");
            assertThatThrownBy(() -> service.createProject(r)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verify(projects,never()).save(any());
        }
        // Không chuyển lỗi query thống kê thành số0; caller phải biết rằng việc tải dữ liệu thất bại.
        @Test void statisticsFailureDoesNotPretendEmptyProjects() {
            when(global.isSystemAdmin()).thenReturn(true); when(projects.findAll()).thenReturn(List.of(Fixtures.project(10)));
            when(stats.read(List.of(10))).thenThrow(new IllegalStateException("database unavailable"));
            assertThatThrownBy(service::getProjects).isInstanceOf(IllegalStateException.class);
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Members {
        @Mock jakarta.persistence.EntityManager entityManager;
        @BeforeEach void projectLock() {
            lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));
            // Actor1 khác member đích2; khai báo rõ lượt đọc actor mới để strict mock không nhầm với stub đích.
            lenient().when(members.findByProject_ProjectIdAndUser_UserId(10,1)).thenReturn(Optional.empty());
        }
        @Mock ProjectRepository projects;
        @Mock UserRepository users;
        @Mock ProjectMemberRepository members;
        @Mock UserRoleRepository roles;
        @Mock UserRoleAssignmentRepository assignments;
        @Mock ProjectSecurity security;
        @Mock AuditLogService audit;
        @Mock NotificationService notifications;
        @InjectMocks ProjectMemberServiceImpl service;
        // Đổi role project giữ thời điểm tham gia và không cấp role toàn cục.
        @Test void memberRoleUpdatePreservesAssignedAt() {
            var member=new ProjectMember(); member.setAssignedAt(java.time.LocalDateTime.of(2026,1,1,0,0)); var assigned=member.getAssignedAt();
            when(members.findByProject_ProjectIdAndUser_UserId(10,2)).thenReturn(Optional.of(member));
            when(roles.findByRoleId(7)).thenReturn(Optional.of(Fixtures.role(7,"DEVELOPER")));
            service.updateRole(10,2,7); assertThat(member.getRole().getRoleId()).isEqualTo(7); assertThat(member.getAssignedAt()).isEqualTo(assigned); verifyNoInteractions(assignments);
        }
        // SYSTEM_ADMIN không phải một role project mới, tránh nhầm assignment toàn cục với membership.
        @Test void projectCannotReceiveSystemAdminRole() {
            when(members.findByProject_ProjectIdAndUser_UserId(10,2)).thenReturn(Optional.of(new ProjectMember()));
            when(roles.findByRoleId(7)).thenReturn(Optional.of(Fixtures.role(7,"SYSTEM_ADMIN")));
            assertThatThrownBy(() -> service.updateRole(10,2,7)).isInstanceOf(com.example.jira.exception.ConflictException.class); verify(members,never()).save(any());
        }
        // Thiếu ADD_MEMBER dừng trước khi đọc user và role.
        @Test void deniedAddDoesNotReadOrWriteMember() {
            assertThatThrownBy(() -> service.addMember(10, new AddMemberRequest())).hasMessageContaining("quyền");
            verify(members).findByProject_ProjectIdAndUser_UserId(10,1);
            verify(members,never()).save(any());verifyNoInteractions(users,roles,notifications);
        }
        @Test void actorMembershipRefreshesAfterLockBeforePermissionCheck() {
            var actor=new ProjectMember();
            when(members.findByProject_ProjectIdAndUser_UserId(10,1)).thenReturn(Optional.of(actor));
            assertThatThrownBy(()->service.addMember(10,new AddMemberRequest())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            var order=inOrder(projects,entityManager,members,security);
            order.verify(projects).findByIdForUpdate(10);order.verify(entityManager).refresh(any(Project.class));
            order.verify(members).findByProject_ProjectIdAndUser_UserId(10,1);order.verify(entityManager).refresh(actor);
            order.verify(security).hasPermission(10,PermissionName.ADD_MEMBER);
            verifyNoInteractions(users,roles,notifications,audit);
        }
        // Thêm thành viên gắn đúng user/role/project, thông báo gửi tới user được thêm.
        @Test void addPersistsMembershipAndNotifiesNewMember() {
            prepareAdd("DEVELOPER"); var r = request(); service.addMember(10, r);
            verify(members).save(argThat(m -> m.getId().equals(new ProjectMemberId(10, 2))
                    && m.getRole().getRoleId() == 7 && m.getAssignedAt() != null));
            verify(notifications).sendNotification(eq(2), isNull(), eq(1), eq(NotificationType.PROJECT_INVITED), anyString());
            verify(audit).logAction(eq("ADD_MEMBER"), eq(EntityType.PROJECT), eq(10), anyString());
        }
        // Thành viên đã có không được thêm lần thứ hai.
        @Test void duplicateMemberIsRejected() {
            prepareAdd("DEVELOPER"); when(members.existsById(new ProjectMemberId(10, 2))).thenReturn(true);
            assertThatThrownBy(() -> service.addMember(10, request())).hasMessageContaining("đã có");
            verify(members, never()).save(any()); verifyNoInteractions(notifications);
        }
        // Project có PM rồi không được thêm PM thứ hai.
        @Test void secondProjectManagerIsRejected() {
            prepareAdd("PROJECT_MANAGER"); when(members.countOtherProjectManagers(10,2)).thenReturn(1L);
            assertThatThrownBy(() -> service.addMember(10, request())).hasMessageContaining("MANAGER");
            verify(members, never()).save(any());
        }
        // Gỡ thành viên chỉ xóa cặp project/user được yêu cầu.
        @Test void removeUsesCompositeIdAndRecordsAudit() {
            when(security.hasPermission(10, PermissionName.DELETE_MEMBER)).thenReturn(true);
            when(members.existsById(new ProjectMemberId(10, 2))).thenReturn(true);
            service.removeMember(10, 2); verify(members).deleteById(new ProjectMemberId(10, 2));
            verify(audit).logAction(eq("REMOVE_MEMBER"), eq(EntityType.PROJECT), eq(10), contains("2"));
        }
        @Test void missingMembershipDoesNotProduceAudit() {
            when(security.hasPermission(10, PermissionName.DELETE_MEMBER)).thenReturn(true);
            assertThatThrownBy(() -> service.removeMember(10, 2)).isInstanceOf(com.example.jira.exception.NotFoundException.class);
            verify(members, never()).deleteById(any());
            verifyNoInteractions(audit);
        }
        // BUG-008: người ngoài không được đọc danh sách thành viên dự án.
        @Test void outsiderCannotListMembers() {
            when(projects.existsById(10)).thenReturn(true);
            assertThatThrownBy(() -> service.getMembers(10)).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(members);
        }
        // Cấu hình dữ liệu đọc của addMember; chưa ghi DB thật ở unit test.
        private void prepareAdd(String role) {
            when(security.hasPermission(10, PermissionName.ADD_MEMBER)).thenReturn(true);
            when(users.findById(2)).thenReturn(Optional.of(Fixtures.user(2)));
            when(roles.findByRoleId(7)).thenReturn(Optional.of(Fixtures.role(7, role)));
        }
        private AddMemberRequest request() { var r = new AddMemberRequest(); r.setUserId(2); r.setRoleId(7); return r; }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Users {
        @Mock AccountManagementService accounts;
        @Mock UserRepository repository;
        @Mock PasswordConfig passwords;
        @Mock GlobalSecurity global;
        @InjectMocks UserServiceImpl service;
        // Endpoint legacy không được đi vòng quyền của trang admin mới.
        @Test void ordinaryUserCannotReadLegacyUserDirectory() {
            doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden")).when(global).assertSystemAdmin();
            assertThatThrownBy(()->service.getAllUsers()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(repository);
        }
        // Danh sách user không trả mật khẩu, kiểm tra dữ liệu DTO thay vì entity thô.
        @Test void listMapsPublicUserData() {
            when(repository.findAll()).thenReturn(List.of(Fixtures.user(2)));
            assertThat(service.getAllUsers()).hasSize(1);
            assertThat(service.getAllUsers().get(0)).hasFieldOrPropertyWithValue("email", "user2@test.invalid");
        }
        // User ID không tồn tại trả lỗi thay vì null.
        @Test void missingUserIsRejected() { assertThatThrownBy(() -> service.getUserById(99)).hasMessageContaining("người dùng"); }
        // Thiếu quyền DELETE_USER không được xóa user.
        @Test void deniedDeleteDoesNotWrite() {
            assertThatThrownBy(() -> service.deleteUser(2)).hasMessageContaining("quyền"); verify(repository, never()).deleteById(anyInt());
        }
        // Có quyền xóa chỉ tác động user được yêu cầu.
        @Test void authorizedDeleteUsesRequestedId() {
            when(global.hasPermission(PermissionName.DELETE_USER)).thenReturn(true);
            service.deleteUser(2); verify(accounts).deleteUser(2); verify(repository,never()).deleteById(anyInt());
        }
    }
}
