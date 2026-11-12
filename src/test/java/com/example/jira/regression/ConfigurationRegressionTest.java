package com.example.jira.regression;

import com.example.jira.dto.permission.*;
import com.example.jira.dto.user_role.*;
import com.example.jira.dto.workflow.*;
import com.example.jira.dto.issue.issue_status.*;
import com.example.jira.dto.issue.issue_type.*;
import com.example.jira.entity.*;
import com.example.jira.enums.*;
import com.example.jira.repository.*;
import com.example.jira.security.*;
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

class ConfigurationRegressionTest {
    @BeforeEach void identifyCaller() { Fixtures.login(1); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    @Nested @ExtendWith(MockitoExtension.class)
    class Roles {
        @Mock GlobalSecurity globalSecurity;
        @Mock UserRoleRepository roles;
        @Mock UserRoleAssignmentRepository assignments;
        @Mock ProjectMemberRepository members;
        @InjectMocks UserRoleServiceImpl service;
        // Vai trò khóa quản trị không được tạo bản sao/đổi tên/xóa qua API cấu hình chung.
        @Test void systemAdminRoleIsReserved() {
            var request=new UserRoleRequest(); request.setRoleName("system_admin");
            assertThatThrownBy(() -> service.createRole(request)).isInstanceOf(com.example.jira.exception.ConflictException.class);
            when(roles.findByRoleId(7)).thenReturn(Optional.of(Fixtures.role(7,"SYSTEM_ADMIN")));
            request.setRoleName("RENAMED");
            assertThatThrownBy(() -> service.updateRole(7,request)).isInstanceOf(com.example.jira.exception.ConflictException.class);
            assertThatThrownBy(() -> service.deleteRole(7)).isInstanceOf(com.example.jira.exception.ConflictException.class);
            verify(roles,never()).save(any()); verify(roles,never()).deleteById(anyInt());
        }
        // Tên role trùng không được tạo bản ghi mới.
        @Test void duplicateRoleIsRejected() {
            when(roles.existsByRoleName("DEVELOPER")).thenReturn(true);
            var r = new UserRoleRequest(); r.setRoleName("DEVELOPER");
            assertThatThrownBy(() -> service.createRole(r)).hasMessageContaining("tồn tại"); verify(roles, never()).save(any());
        }
        // Tạo role hợp lệ giữ đúng tên trong response.
        @Test void createRoleReturnsSavedName() {
            when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
            var r = new UserRoleRequest(); r.setRoleName("REVIEWER");
            assertThat(service.createRole(r).getRoleName()).isEqualTo("REVIEWER");
        }
        // Role đang gán toàn cục không được xóa.
        @Test void assignedRoleCannotBeDeleted() {
            when(assignments.existsByRole_RoleId(7)).thenReturn(true);
            assertThatThrownBy(() -> service.deleteRole(7)).hasMessageContaining("đang được gán"); verify(roles, never()).deleteById(anyInt());
        }
        // Role trong project cũng không được xóa dù chưa gán toàn cục.
        @Test void projectRoleCannotBeDeleted() {
            when(members.existsByRole_RoleId(7)).thenReturn(true);
            assertThatThrownBy(() -> service.deleteRole(7)).hasMessageContaining("đang được gán"); verify(roles, never()).deleteById(anyInt());
        }
        // Role rỗng không còn người dùng được xóa đúng ID.
        @Test void unusedRoleCanBeDeleted() { service.deleteRole(7); verify(roles).deleteById(7); }
        // Cập nhật role không tồn tại không được ghi mới.
        @Test void missingRoleUpdateIsRejected() {
            assertThatThrownBy(() -> service.updateRole(7, new UserRoleRequest())).hasMessageContaining("vai trò"); verify(roles, never()).save(any());
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Permissions {
        @Mock GlobalSecurity globalSecurity;
        @Mock ProjectSecurity projectSecurity;
        @BeforeEach void callerIdentity() { lenient().when(globalSecurity.currentUserId()).thenReturn(1); }
        @Mock ProjectMemberRepository members;
        @Mock UserRoleAssignmentRepository assignments;
        @Mock RolePermissionRepository grants;
        @Mock PermissionRepository permissions;
        @InjectMocks PermissionServiceImpl service;
        // Permission trùng không được tạo lại.
        @Test void duplicatePermissionIsRejected() {
            when(permissions.existsByPermissionName("EDIT_ISSUE")).thenReturn(true);
            var r = new PermissionRequest(); r.setPermissionName("EDIT_ISSUE");
            assertThatThrownBy(() -> service.createPermission(r)).hasMessageContaining("tồn tại"); verify(permissions, never()).save(any());
        }
        // Permission mới giữ đúng mã dùng để kiểm quyền.
        @Test void createPermissionPreservesCode() {
            when(permissions.save(any())).thenAnswer(i -> i.getArgument(0));
            var r = new PermissionRequest(); r.setPermissionName("EDIT_ISSUE");
            assertThat(service.createPermission(r).getPermissionName()).isEqualTo("EDIT_ISSUE");
        }
        // Permission đã được role sử dụng phải giữ lại.
        @Test void usedPermissionCannotBeDeleted() {
            var p = new Permission(); p.setPermissionId(8); when(permissions.findById(8)).thenReturn(Optional.of(p));
            when(grants.existsByPermission_PermissionId(8)).thenReturn(true);
            assertThatThrownBy(() -> service.deletePermission(8)).hasMessageContaining("đang được sử dụng"); verify(permissions, never()).delete(any());
        }
        // Permission không tồn tại trả lỗi thay vì delete im lặng.
        @Test void missingPermissionIsRejected() {
            assertThatThrownBy(() -> service.deletePermission(8)).hasMessageContaining("quyền"); verify(permissions, never()).delete(any());
        }
        // Không có vai trò toàn cục lẫn membership thì không có quyền.
        @Test void outsiderHasNoPermission() { assertThat(service.checkPermission(1, "EDIT_ISSUE", 10)).isFalse(); }
        // Quyền từ role của membership phải tìm theo đúng project và user.
        @Test void projectPermissionUsesScopedMembership() {
            when(projectSecurity.hasPermission(10, PermissionName.EDIT_ISSUE)).thenReturn(true);
            assertThat(service.checkPermission(1, "EDIT_ISSUE", 10)).isTrue();
            verify(projectSecurity).hasPermission(10, PermissionName.EDIT_ISSUE);
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class RolePermissions {
        @Mock GlobalSecurity globalSecurity;
        @Mock UserRoleRepository roles;
        @Mock PermissionRepository permissions;
        @Mock RolePermissionRepository grants;
        @InjectMocks RolePermissionServiceImpl service;
        // Không có role thì không tạo liên kết permission.
        @Test void missingRoleStopsGrant() {
            assertThatThrownBy(() -> service.addPermissionToRole(7, 8)).hasMessageContaining("vai trò"); verifyNoInteractions(grants);
        }
        // Liên kết đã có phải báo trùng, không ghi lại.
        @Test void duplicateGrantIsRejected() {
            setupGrant(); when(grants.existsById(new RolePermissionId(7, 8))).thenReturn(true);
            assertThatThrownBy(() -> service.addPermissionToRole(7, 8)).hasMessageContaining("đã được gán"); verify(grants, never()).save(any());
        }
        // Gỡ permission chỉ xóa liên kết role/permission, không xóa hai bảng gốc.
        @Test void revokeDeletesOnlyGrant() {
            var rp = new RolePermission(); when(grants.findByRole_RoleIdAndPermission_PermissionId(7, 8)).thenReturn(Optional.of(rp));
            service.removePermissionToRole(7, 8); verify(grants).delete(rp); verifyNoInteractions(roles, permissions);
        }
        // BUG-009: user thường không được sửa quyền của một vai trò toàn hệ thống.
        @Test void ordinaryUserCannotGrantPermissions() {
            doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden")).when(globalSecurity).assertSystemAdmin();
            assertThatThrownBy(() -> service.addPermissionToRole(7, 8)).isInstanceOf(RuntimeException.class);
            verify(globalSecurity).assertSystemAdmin();
            verify(grants, never()).save(any());
        }
        private void setupGrant() {
            when(roles.findByRoleId(7)).thenReturn(Optional.of(Fixtures.role(7, "DEVELOPER")));
            var p = new Permission(); p.setPermissionId(8); p.setPermissionName("MANAGE_SPRINT");
            when(permissions.findById(8)).thenReturn(Optional.of(p));
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class IssueTypes {
        @Mock IssueTypeRepository types;
        @Mock GlobalSecurity global;
        @Mock ProjectIssueTypeRepository projects;
        @Mock IssueRepository issues;
        @InjectMocks IssueTypeServiceImpl service;
        @BeforeEach void existingType(){var row=new IssueType();row.setIssueTypeId(7);lenient().when(types.findById(7)).thenReturn(Optional.of(row));}
        // Loại đang bật trong project không được xóa.
        @Test void configuredTypeCannotBeDeleted() {
            when(projects.existsByIssueType_IssueTypeId(7)).thenReturn(true);
            assertThatThrownBy(() -> service.deleteIssueType(7)).hasMessageContaining("dự án"); verify(types, never()).deleteById(anyInt());
        }
        // Loại đã có issue sử dụng cũng phải giữ lại.
        @Test void usedTypeCannotBeDeleted() {
            when(issues.existsByIssueType_IssueTypeId(7)).thenReturn(true);
            assertThatThrownBy(() -> service.deleteIssueType(7)).hasMessageContaining("sử dụng"); verify(types, never()).deleteById(anyInt());
        }
        // Loại không còn tham chiếu được xóa đúng ID.
        @Test void unusedTypeCanBeDeleted() { when(types.deleteCatalogRow(7)).thenReturn(1);service.deleteIssueType(7); verify(types).deleteCatalogRow(7); }
        // Tạo issue type giữ tên và kiểm tra DTO trả về.
        @Test void createTypePreservesName() {
            when(types.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            var r = new IssueTypeRequest(); r.setTypeName("Bug");
            assertThat(service.createIssueType(r).getTypeName()).isEqualTo("Bug");
        }
        // Không tìm thấy issue type khi cập nhật không được save.
        @Test void updateMissingTypeIsRejected() {
            assertThatThrownBy(() -> service.updateIssueType(8, typeRequest("Missing"))).hasMessageContaining("Issue Type");
            verify(types, never()).save(any());
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Statuses {
        @Mock GlobalSecurity globalSecurity;
        @Mock IssueStatusRepository statuses;
        @Mock IssueRepository issues;
        @Mock BoardColumnStatusRepository mappings; @Mock WorkflowRuleRepository rules; @Mock IssueTransitionRepository transitions;
        @InjectMocks IssueStatusServiceImpl service;
        @BeforeEach void existingStatus(){lenient().when(statuses.findById(3)).thenReturn(Optional.of(Fixtures.status(3,"Done",StatusCategory.DONE)));}
        // Nhóm không đổi sau tạo; từ chối trước sửa tên để bảo toàn ý nghĩa lịch sử Scrum.
        @Test void categoryIsImmutableAndRenamePreservesLinks() {
            assertThatThrownBy(()->service.updateStatus(3,statusRequest("Reopened",StatusCategory.TO_DO))).hasMessageContaining("cố định");
            verify(statuses,never()).saveAndFlush(any());
            when(statuses.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
            var response=service.updateStatus(3,statusRequest(" Finished ",StatusCategory.DONE));
            assertThat(response.getStatusName()).isEqualTo("Finished");assertThat(response.getStatusCategory()).isEqualTo(StatusCategory.DONE);
        }
        // Lịch sử còn tham chiếu thì không xóa status dù không còn issue hiện tại dùng nó.
        @Test void historicalStatusCannotBeDeleted() {
            when(transitions.existsByFromStatus_StatusIdOrToStatus_StatusId(3,3)).thenReturn(true);
            assertThatThrownBy(()->service.deleteStatus(3)).hasMessageContaining("lịch sử");verify(statuses,never()).deleteById(anyInt());
        }
        @Test void mappedStatusCannotBeDeleted() {
            when(mappings.existsByIssueStatus_StatusId(3)).thenReturn(true);
            assertThatThrownBy(()->service.deleteStatus(3)).hasMessageContaining("mapping");
            verify(statuses,never()).deleteById(anyInt());verifyNoInteractions(rules,transitions);
        }
        @Test void workflowStatusCannotBeDeleted() {
            when(rules.existsByFromStatus_StatusIdOrToStatus_StatusId(3,3)).thenReturn(true);
            assertThatThrownBy(()->service.deleteStatus(3)).hasMessageContaining("workflow");verify(statuses,never()).deleteById(anyInt());
        }
        @Test void emptyStatusCannotBeSaved() {
            assertThatThrownBy(()->service.createStatus(statusRequest(" ",StatusCategory.DONE))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verifyNoInteractions(statuses);
        }
        // Trạng thái trùng tên không được thêm mới.
        @Test void duplicateStatusIsRejected() {
            when(statuses.existsByStatusName("Done")).thenReturn(true);
            var r = new IssueStatusRequest(); r.setStatusName("Done"); r.setStatusCategory(StatusCategory.DONE);
            assertThatThrownBy(() -> service.createStatus(r)).hasMessageContaining("đã có"); verify(statuses, never()).save(any());
        }
        // Tạo trạng thái giữ cả tên hiển thị và nhóm DONE.
        @Test void createStatusPreservesCategory() {
            when(statuses.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            var r = new IssueStatusRequest(); r.setStatusName("Completed"); r.setStatusCategory(StatusCategory.DONE);
            assertThat(service.createStatus(r).getStatusCategory()).isEqualTo(StatusCategory.DONE);
        }
        // Trạng thái đang có issue dùng không được xóa.
        @Test void usedStatusCannotBeDeleted() {
            when(issues.existsByStatus_StatusId(3)).thenReturn(true);
            assertThatThrownBy(() -> service.deleteStatus(3)).hasMessageContaining("sử dụng"); verify(statuses, never()).deleteById(anyInt());
        }
        // Cập nhật trạng thái không tồn tại không được tạo mới.
        @Test void missingStatusUpdateIsRejected() {
            assertThatThrownBy(() -> service.updateStatus(4, statusRequest("Missing",StatusCategory.DONE))).hasMessageContaining("trạng thái");
            verify(statuses, never()).save(any());
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Workflow {
        @Mock GlobalSecurity globalSecurity;
        @Mock WorkflowRuleRepository rules;
        @Mock IssueStatusRepository statuses;
        @InjectMocks WorkflowRuleServiceImpl service;
        @Test void selfTransitionIsRejectedBeforeDatabaseRead() {
            var r=request();r.setToStatusId(1);
            assertThatThrownBy(()->service.createRule(r)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verifyNoInteractions(rules,statuses);
        }
        // Workflow trùng cặp nguồn/đích phải bị từ chối.
        @Test void duplicateTransitionRuleIsRejected() {
            when(rules.existsByFromStatus_StatusIdAndToStatus_StatusId(1, 2)).thenReturn(true);
            var r = request(); assertThatThrownBy(() -> service.createRule(r)).hasMessageContaining("tồn tại"); verify(rules, never()).save(any());
        }
        // Tạo rule gắn đúng from và to, không đảo hai ID.
        @Test void rulePreservesDirection() {
            when(statuses.findById(1)).thenReturn(Optional.of(Fixtures.status(1, "To Do", StatusCategory.TO_DO)));
            when(statuses.findById(2)).thenReturn(Optional.of(Fixtures.status(2, "Doing", StatusCategory.IN_PROGRESS)));
            when(rules.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0)); service.createRule(request());
            verify(rules).saveAndFlush(argThat(r -> r.getFromStatus().getStatusId() == 1 && r.getToStatus().getStatusId() == 2));
        }
        // Thiếu trạng thái nguồn không được lưu rule mồ côi.
        @Test void missingSourceStatusIsRejected() {
            assertThatThrownBy(() -> service.createRule(request())).hasMessageContaining("bắt đầu"); verify(rules, never()).save(any());
        }
        // Xóa chỉ tác động rule tìm được, không xóa trạng thái.
        @Test void deleteRuleDoesNotDeleteStatuses() {
            var r = new WorkflowRule(); when(rules.findById(9)).thenReturn(Optional.of(r));
            service.deleteRule(9); verify(rules).delete(r); verifyNoInteractions(statuses);
        }
        // Lấy trạng thái đích chỉ từ rule có nguồn tương ứng.
        @Test void allowedTransitionsUseSourceStatus() {
            assertThat(service.getAllowedNextStatuses(1)).isEmpty(); verify(rules).findByFromStatus_StatusId(1);
        }
        private WorkflowRuleRequest request() { var r = new WorkflowRuleRequest(); r.setFromStatusId(1); r.setToStatusId(2); return r; }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class ProjectTypes {
        @Mock ProjectSecurity projectSecurity;
        @Mock ProjectIssueTypeRepository links;
        @Mock ProjectRepository projects;
        @Mock IssueRepository issues;
        @Mock IssueTypeRepository types;
        @Mock jakarta.persistence.EntityManager em;
        @InjectMocks ProjectIssueTypeServiceImpl service;
        @BeforeEach void lockedProject(){lenient().when(projects.findByIdForUpdate(10)).thenReturn(Optional.of(Fixtures.project(10)));}
        // Gán default=true phải flush false trước ghi true để không vi phạm unique partial index.
        @Test void assignDefaultClearsOldBeforeInsert() {
            var old=link(8,true);when(links.findByProject_ProjectId(10)).thenReturn(List.of(old));
            var type=new IssueType();type.setIssueTypeId(7);when(types.findById(7)).thenReturn(Optional.of(type));
            service.assignIssueType(10,7,true);assertThat(old.isDefault()).isFalse();
            var order=inOrder(links);order.verify(links).saveAll(List.of(old));order.verify(links).flush();
            order.verify(links).saveAndFlush(argThat(l->l.isDefault()&&l.getIssueType().getIssueTypeId()==7));
            verify(projectSecurity,times(2)).requireIssueTypeManagement(10);
        }
        // Quyền bị thu hồi khi đợi project lock phải chặn ghi dù lần kiểm đầu đã cho phép.
        @Test void revokedPermissionAfterLockStopsMutation() {
            doNothing().doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(projectSecurity).requireIssueTypeManagement(10);
            assertThatThrownBy(()->service.assignIssueType(10,7,false)).hasMessageContaining("revoked");verifyNoInteractions(links,types);
        }
        // Loại có issue dùng trong đúng project thì không gỡ cấu hình.
        @Test void usedTypeCannotBeUnassigned() {
            when(links.findById(new ProjectIssueTypeId(10,7))).thenReturn(Optional.of(link(7,false)));
            when(issues.existsByProject_ProjectIdAndIssueType_IssueTypeId(10, 7)).thenReturn(true);
            assertThatThrownBy(() -> service.removeIssueType(10, 7)).hasMessageContaining("sử dụng"); verify(links, never()).deleteById(any());
        }
        // Không được gỡ loại mặc định khi chưa đổi sang loại khác.
        @Test void defaultTypeCannotBeRemoved() {
            var link = link(7, true); when(links.findById(new ProjectIssueTypeId(10, 7))).thenReturn(Optional.of(link));
            assertThatThrownBy(() -> service.removeIssueType(10, 7)).hasMessageContaining("mặc định"); verify(links, never()).deleteById(any());
        }
        // Chọn mặc định mới phải tắt mặc định cũ trong cùng project.
        @Test void settingDefaultClearsPreviousDefault() {
            var old = link(7, true); var next = link(8, false);
            when(links.findByProject_ProjectId(10)).thenReturn(List.of(old, next)); service.setDefaultIssueType(10, 8);
            assertThat(old.isDefault()).isFalse(); assertThat(next.isDefault()).isTrue(); verify(links).saveAll(List.of(old, next));
        }
        // Chọn loại chưa gán phải báo lỗi và không saveAll.
        @Test void unassignedTypeCannotBecomeDefault() {
            when(links.findByProject_ProjectId(10)).thenReturn(List.of(link(7, false)));
            assertThatThrownBy(() -> service.setDefaultIssueType(10, 8)).hasMessageContaining("chưa được gán"); verify(links, never()).saveAll(any());
        }
        // Gán loại tạo khóa ghép project/type, không nhầm loại ở dự án khác.
        @Test void assignStoresScopedCompositeId() {

            var t = new IssueType(); t.setIssueTypeId(7); when(types.findById(7)).thenReturn(Optional.of(t));
            service.assignIssueType(10, 7, false); verify(links).saveAndFlush(argThat(l -> l.getId().equals(new ProjectIssueTypeId(10, 7))));
        }
        private ProjectIssueType link(int typeId, boolean defaultType) {
            var t = new IssueType(); t.setIssueTypeId(typeId);
            return new ProjectIssueType(new ProjectIssueTypeId(10, typeId), Fixtures.project(10), t, defaultType);
        }
    }

    private static IssueTypeRequest typeRequest(String name){var r=new IssueTypeRequest();r.setTypeName(name);return r;}
    private static IssueStatusRequest statusRequest(String name,StatusCategory category){var r=new IssueStatusRequest();r.setStatusName(name);r.setStatusCategory(category);return r;}
}
