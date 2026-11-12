package com.example.jira.regression;

import com.example.jira.enums.PermissionName;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.impl.AccountContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Kiểm context không tự cấp quyền, không nhận target user và không lộ project người ngoài. */
@ExtendWith(MockitoExtension.class)
class AccountContextRegressionTest {
    @Mock UserRepository users;
    @Mock ProjectRepository projects;
    @Mock GlobalSecurity global;
    @Mock ProjectSecurity projectSecurity;
    @InjectMocks AccountContextService service;

    /** Phiên user 8 chỉ nhận chính hồ sơ của mình; context toàn cục không đọc dự án. */
    private void actor() {
        when(global.currentUserId()).thenReturn(8);
        when(users.findById(8)).thenReturn(Optional.of(Fixtures.user(8)));
    }

    @Test void globalContextKeepsOnlyEffectivePermissionsAndNoProject() {
        actor(); when(global.hasPermission(PermissionName.CREATE_PROJECT)).thenReturn(true);
        var result = service.getAccountContext(null);
        assertThat(result.user().getUserId()).isEqualTo(8);
        assertThat(result.globalPermissions()).containsExactly("CREATE_PROJECT");
        assertThat(result.systemAdmin()).isFalse(); assertThat(result.projectId()).isNull();
        assertThat(result.projectPermissions()).isEmpty(); verifyNoInteractions(projects, projectSecurity);
    }

    @Test void projectPermissionsDoNotBecomeGlobalPermissions() {
        actor(); when(projects.existsById(10)).thenReturn(true);
        when(projectSecurity.hasPermission(eq(10), any(PermissionName.class)))
                .thenAnswer(call -> call.getArgument(1) == PermissionName.VIEW_PROJECT);
        var result = service.getAccountContext(10);
        verify(projectSecurity).requirePermission(10, PermissionName.VIEW_PROJECT);
        assertThat(result.projectPermissions()).containsExactly("VIEW_PROJECT");
        assertThat(result.globalPermissions()).isEmpty(); assertThat(result.projectId()).isEqualTo(10);
    }

    @Test void outsiderStopsBeforePermissionEnumeration() {
        actor(); when(projects.existsById(10)).thenReturn(true);
        doThrow(new AccessDeniedException("forbidden")).when(projectSecurity).requirePermission(10, PermissionName.VIEW_PROJECT);
        assertThatThrownBy(() -> service.getAccountContext(10)).isInstanceOf(AccessDeniedException.class);
        verify(global, never()).hasPermission(any());
    }

    @Test void missingAndInvalidProjectStopBeforePermissionReads() {
        actor();
        assertThatThrownBy(() -> service.getAccountContext(10)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.getAccountContext(0)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(projectSecurity); verify(global, never()).hasPermission(any());
    }

    @Test void disabledAccountCannotReceiveContext() {
        when(global.currentUserId()).thenReturn(8);
        var user = Fixtures.user(8); user.setEnabled(false);
        when(users.findById(8)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.getAccountContext(null)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(projectSecurity, projects);
    }
}
