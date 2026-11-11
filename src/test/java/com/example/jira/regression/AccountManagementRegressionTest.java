package com.example.jira.regression;

import com.example.jira.dto.account.AccountRequests.*;
import com.example.jira.entity.*;
import com.example.jira.enums.EntityType;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.service.AuditLogService;
import com.example.jira.service.impl.AccountManagementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountManagementRegressionTest {
    @Mock UserRepository users; @Mock UserRoleRepository roles; @Mock UserRoleAssignmentRepository assignments;
    @Mock RefreshTokenRepository tokens; @Mock GlobalSecurity global; @Mock PasswordEncoder encoder;
    @Mock EntityManager entityManager; @Mock AuditLogService audit;
    @InjectMocks AccountManagementService service;
    User actor,target; UserRole adminRole;
    @BeforeEach void fixtures() {
        actor=Fixtures.user(1); target=Fixtures.user(2); adminRole=Fixtures.role(7,"SYSTEM_ADMIN");
        lenient().when(global.currentUserId()).thenReturn(1);
        lenient().when(users.findByIdForUpdate(1)).thenReturn(Optional.of(actor)); lenient().when(users.findByIdForUpdate(2)).thenReturn(Optional.of(target));
        lenient().when(roles.lockAdminRoles()).thenReturn(List.of(adminRole)); lenient().when(roles.findByRoleId(7)).thenReturn(Optional.of(adminRole));
    }
    @Test void profileOnlyEditsCurrentUserAndKeepsEmail() {
        var result=service.profile(new Profile(" changed ","Display")); assertThat(result.getUserId()).isEqualTo(1); assertThat(result.isEnabled()).isTrue();
        assertThat(actor.getUserName()).isEqualTo("changed"); assertThat(actor.getEmail()).isEqualTo("user1@test.invalid"); verify(users,never()).findByIdForUpdate(2);
    }
    @Test void blankProfileNameStopsBeforeLock() {
        status(() -> service.profile(new Profile(" ",null)),400); verifyNoInteractions(users);
    }
    @Test void wrongPasswordDoesNotChangeHashVersionOrSessions() {
        status(() -> service.password(new Password("old-password","new-password")),400);
        assertThat(actor.getPasswordHash()).isEqualTo("hash"); assertThat(actor.getAuthVersion()).isZero(); verifyNoInteractions(tokens,audit);
    }
    @Test void passwordUpdatesHashStampAndRevokesAllRefresh() {
        when(encoder.matches("old-password","hash")).thenReturn(true); when(encoder.encode("new-password")).thenReturn("encoded-new");
        service.password(new Password("old-password","new-password"));
        assertThat(actor.getPasswordHash()).isEqualTo("encoded-new"); assertThat(actor.getAuthVersion()).isEqualTo(1);
        verify(tokens).revokeAllForUser(eq(1),any()); verify(audit).logAction(eq("CHANGE_PASSWORD"),eq(EntityType.USER),eq(1),argThat(text -> !text.contains("new-password")));
    }
    @Test void invalidUtf8PasswordStopsBeforeDatabase() {
        status(() -> service.password(new Password("old-password","ấ".repeat(25))),400); verifyNoInteractions(users,encoder,tokens);
    }
    @Test void disabledCurrentUserCannotEditProfile() {
        actor.setEnabled(false); status(() -> service.profile(new Profile("name",null)),401); verify(users,never()).save(any());
    }
    @Test void grantUsesGlobalCompositeKeyAndTimestamp() {
        service.grantRole(2,7);
        verify(assignments).save(argThat(value -> value.getId().equals(new UserRoleAssignmentId(2,7)) && value.getUser()==target && value.getRole()==adminRole && value.getAssignedAt()!=null));
        var order=inOrder(roles,users); order.verify(roles).lockAdminRoles(); order.verify(users).findByIdForUpdate(1); order.verify(users).findByIdForUpdate(2);
    }
    @Test void duplicateGrantAndMissingRevokeAreNoOps() {
        when(assignments.existsById(new UserRoleAssignmentId(2,7))).thenReturn(true);
        service.grantRole(2,7); verify(assignments,never()).save(any());
        when(assignments.existsById(new UserRoleAssignmentId(2,7))).thenReturn(false);
        service.revokeRole(2,7); verify(assignments,never()).deleteById(any());
    }
    @Test void lastEnabledAdminCannotBeRevokedOrDisabled() {
        when(assignments.existsById(new UserRoleAssignmentId(2,7))).thenReturn(true); when(assignments.countEnabledAdmins()).thenReturn(1L); when(assignments.isAdmin(2)).thenReturn(true);
        status(() -> service.revokeRole(2,7),409); status(() -> service.enabled(2,false),409);
        assertThat(target.isEnabled()).isTrue(); assertThat(target.getAuthVersion()).isZero(); verifyNoInteractions(tokens); verify(assignments,never()).deleteById(any());
    }
    @Test void anotherEnabledAdminAllowsRevoke() {
        when(assignments.existsById(new UserRoleAssignmentId(2,7))).thenReturn(true); when(assignments.countEnabledAdmins()).thenReturn(2L);
        service.revokeRole(2,7); verify(assignments).deleteById(new UserRoleAssignmentId(2,7));
    }
    @Test void disablingAndReenablingInvalidateOldSessions() {
        service.enabled(2,false); assertThat(target.isEnabled()).isFalse(); assertThat(target.getAuthVersion()).isEqualTo(1);
        service.enabled(2,true); assertThat(target.isEnabled()).isTrue(); assertThat(target.getAuthVersion()).isEqualTo(2);
        verify(tokens,times(2)).revokeAllForUser(eq(2),any());
    }
    @Test void permissionIsRecheckedAfterWaitingForAdminLock() {
        doNothing().doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(global).assertSystemAdmin();
        assertThatThrownBy(() -> service.grantRole(2,7)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(users,never()).findByIdForUpdate(2); verify(assignments,never()).save(any());
    }
    @Test void roleBearingUserMustRevokeRolesBeforeDeletion() {
        var assignment=new UserRoleAssignment(); assignment.setRole(adminRole); when(assignments.findByUser_UserId(2)).thenReturn(List.of(assignment));
        status(() -> service.deleteUser(2),409); verify(users,never()).delete(any(User.class));
    }
    private void status(Runnable call,int expected) { assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode().value()).isEqualTo(expected)); }
}
