package com.example.jira.regression;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.enums.PermissionName;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleScopeRegressionTest {
 @Mock GlobalSecurity global; @Mock UserRoleRepository roles; @Mock UserRepository users; @Mock EntityManager em;
 @InjectMocks AdminRoleLock lock;
 /** Actor bị disable trong lúc chờ khóa không được ghi role bằng trạng thái trước đó. */
 @Test void disabledActorAfterLockCannotMutate(){
  var actor=Fixtures.user(1);actor.setEnabled(false);
  when(roles.lockAdminRoles()).thenReturn(List.of(Fixtures.role(1,"SYSTEM_ADMIN")));
  when(global.currentUserId()).thenReturn(1);when(users.findByIdForUpdate(1)).thenReturn(Optional.of(actor));
  assertThatThrownBy(lock::begin).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  verify(em).refresh(actor);verify(global,times(1)).assertSystemAdmin();
 }
 /** Quyền admin bị gỡ sau kiểm lần đầu được phát hiện sau khi đã khóa/refresh actor. */
 @Test void revokedAdminRecheckedAfterWait(){
  var actor=Fixtures.user(1);when(roles.lockAdminRoles()).thenReturn(List.of(Fixtures.role(1,"SYSTEM_ADMIN")));
  when(global.currentUserId()).thenReturn(1);when(users.findByIdForUpdate(1)).thenReturn(Optional.of(actor));
  doNothing().doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(global).assertSystemAdmin();
  assertThatThrownBy(lock::begin).hasMessage("revoked");
  var order=inOrder(global,roles,users,em);order.verify(global).assertSystemAdmin();order.verify(roles).lockAdminRoles();
  order.verify(global).currentUserId();order.verify(users).findByIdForUpdate(1);order.verify(em).refresh(actor);order.verify(global).assertSystemAdmin();
 }
 /** Permission quản trị hoặc alias không được cấp cho role project qua route cũ. */
 @Test void projectRoleCannotReceiveGlobalOrUnknownPermissions(){
  for(String code:List.of("DELETE_PROJECT","CREATE_PROJECT","DELETE_USER","CREATE_ISSUE_TYPE","made_up","edit_issue"))
   assertThatThrownBy(()->RolePolicy.projectGrant("DEVELOPER",code)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatCode(()->RolePolicy.projectGrant("DEVELOPER","EDIT_ISSUE")).doesNotThrowAnyException();
 }
 /** Role seed và mã tên chuẩn hóa dùng cùng quy tắc ở create/update. */
 @Test void canonicalNamesAndBuiltInsAreStable(){
  assertThat(RolePolicy.roleName(" reviewer ")).isEqualTo("REVIEWER");
  for(String name:List.of(" system_admin ","PROJECT_MANAGER","scrum_master","DEVELOPER"))assertThat(RolePolicy.reserved(name)).isTrue();
  for(String name:List.of("","1A","A B","a".repeat(51)))assertThatThrownBy(()->RolePolicy.roleName(name)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 /** Assignment DEVELOPER global không tạo quyền xóa tài nguyên hệ thống; tạo mới vẫn cho user đã xác thực. */
 @Test void globalLegacyDoesNotBecomeSystemAuthority(){
  Fixtures.login(1);
  try{
   var assignments=mock(UserRoleAssignmentRepository.class);var service=new GlobalSecurity(assignments);
   var a=new UserRoleAssignment();a.setRole(Fixtures.role(7,"DEVELOPER"));when(assignments.findByUser_UserId(1)).thenReturn(List.of(a));
   assertThat(service.hasPermission(PermissionName.DELETE_PROJECT)).isFalse();assertThat(service.hasPermission(PermissionName.DELETE_USER)).isFalse();
   assertThat(service.hasPermission(PermissionName.CREATE_PROJECT)).isTrue();
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}
