package com.example.jira.regression;

import com.example.jira.entity.*;
import com.example.jira.enums.PermissionName;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.security.jwt.JwtUtil;
import com.example.jira.ultils.SecurityUtils;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTest {
    static final String SECRET = "regression-test-only-key-012345678901234567890123456789";
    @BeforeEach void login() { Fixtures.login(1); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    @Nested @ExtendWith(MockitoExtension.class)
    class ProjectPermissions {
        @Mock ProjectMemberRepository members;
        @Mock RolePermissionRepository grants;
        @Mock ProjectRepository projects;
        @Mock GlobalSecurity global;
        @InjectMocks ProjectSecurity service;
        // Lead đúng project được thao tác mà không cần membership riêng.
        @Test void leadHasProjectPermission() {
            var p = Fixtures.project(10); p.setLead(Fixtures.user(1)); when(projects.findById(10)).thenReturn(Optional.of(p));
            assertThat(service.hasPermission(10, PermissionName.MANAGE_SPRINT)).isTrue(); verifyNoInteractions(members, grants);
        }
        // Người ngoài project không được hưởng quyền chỉ vì biết project ID.
        @Test void outsiderIsDenied() { assertThat(service.hasPermission(10, PermissionName.EDIT_ISSUE)).isFalse(); }
        // Thành viên được đọc dự án kể cả role chưa có grant VIEW_PROJECT riêng.
        @Test void memberCanViewWithoutExplicitGrant() {
            var member = new ProjectMember(); member.setRole(Fixtures.role(7, "DEVELOPER"));
            when(members.findByProject_ProjectIdAndUser_UserId(10, 1)).thenReturn(Optional.of(member));
            assertThat(service.hasPermission(10, PermissionName.VIEW_PROJECT)).isTrue();
            verifyNoInteractions(grants);
        }
        // Admin toàn cục được quản lý dự án không có membership; không tra role của project khác.
        @Test void globalAdminCanManageProject() {
            when(global.isSystemAdmin()).thenReturn(true);
            assertThat(service.hasPermission(10, PermissionName.MANAGE_BOARD)).isTrue();
            verifyNoInteractions(projects, members, grants);
        }
        // Thành viên chỉ có quyền mà role hiện tại được gán.
        @Test void memberPermissionIsCheckedAgainstRole() {
            var m = new ProjectMember(); m.setRole(Fixtures.role(7, "DEVELOPER"));
            when(members.findByProject_ProjectIdAndUser_UserId(10, 1)).thenReturn(Optional.of(m));
            when(grants.existsByRole_RoleIdAndPermission_PermissionName(7, "EDIT_ISSUE")).thenReturn(true);
            assertThat(service.hasPermission(10, PermissionName.EDIT_ISSUE)).isTrue();
            assertThat(service.hasPermission(10, PermissionName.MANAGE_SPRINT)).isFalse();
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class GlobalPermissions {
        @Mock UserRoleAssignmentRepository assignments;
        @Mock RolePermissionRepository grants;
        @InjectMocks GlobalSecurity service;
        // Không cần role toàn cục: người đã xác thực vẫn được tạo project của mình.
        @Test void noGlobalRoleCanCreateOwnProject() { assertThat(service.hasPermission(PermissionName.CREATE_PROJECT)).isTrue(); }
        // Có một grant không biến role thường thành quản trị hệ thống.
        @Test void ordinaryRoleCannotAdministerSystem() {
            var assignment = new UserRoleAssignment(); assignment.setRole(Fixtures.role(7, "DEVELOPER"));
            when(assignments.findByUser_UserId(1)).thenReturn(List.of(assignment));
            assertThatThrownBy(service::assertSystemAdmin).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(grants);
        }
        // SYSTEM_ADMIN phải nằm trong gán role toàn cục của chính user đang đăng nhập.
        @Test void globalAdminPassesAdministrationGate() {
            var assignment = new UserRoleAssignment(); assignment.setRole(Fixtures.role(8, "SYSTEM_ADMIN"));
            when(assignments.findByUser_UserId(1)).thenReturn(List.of(assignment));
            assertThatCode(service::assertSystemAdmin).doesNotThrowAnyException();
        }
        // Không có phiên đăng nhập thì dừng 401 trước khi đọc bảng phân quyền.
        @Test void missingIdentityStopsBeforeDatabase() {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(service::currentUserId).isInstanceOf(org.springframework.security.core.AuthenticationException.class);
            verifyNoInteractions(assignments, grants);
        }
        // SYSTEM_ADMIN vẫn được nhận diện khi đứng sau assignment legacy không phải admin.
        @Test void permissionCanComeFromSecondRole() {
            var a = new UserRoleAssignment(); a.setRole(Fixtures.role(7, "DEVELOPER"));
            var b = new UserRoleAssignment(); b.setRole(Fixtures.role(8, "SYSTEM_ADMIN"));
            when(assignments.findByUser_UserId(1)).thenReturn(List.of(a, b));
            assertThat(service.hasPermission(PermissionName.DELETE_PROJECT)).isTrue();
        }
    }
    @Nested class Jwt {
        private JwtUtil jwt;
        @BeforeEach void initializeTestKey() {
            jwt = new JwtUtil(); ReflectionTestUtils.setField(jwt, "secret", SECRET);
            ReflectionTestUtils.setField(jwt, "expiration", 60000L);
        }
        // Token ký bằng khóa test phải đọc lại đúng chủ thể.
        @Test void signedTokenRoundTrip() {
            var token = jwt.generateToken("user1@test.invalid");
            assertThat(jwt.extractUsername(token)).isEqualTo("user1@test.invalid");
            assertThat(jwt.validateToken(token, "user1@test.invalid")).isTrue();
        }
        // Token hợp lệ của user khác không được xác thực cho người đang kiểm tra.
        @Test void tokenForDifferentSubjectIsRejected() {
            assertThat(jwt.validateToken(jwt.generateToken("user2@test.invalid"), "user1@test.invalid")).isFalse();
        }
        // JWT đã hết hạn phải bị phát hiện bởi bộ parser, không phụ thuộc sleep.
        @Test void expiredTokenIsDetected() {
            var token = Jwts.builder().setSubject("user1@test.invalid").setExpiration(new Date(0))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
            assertThatThrownBy(() -> jwt.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
        }
        // JWT không có cấu trúc hợp lệ không được trả subject.
        @Test void malformedTokenIsDetected() {
            assertThatThrownBy(() -> jwt.extractUsername("not-a-jwt")).isInstanceOf(JwtException.class);
        }
        // Chữ ký bằng khóa khác không được tin cậy.
        @Test void wrongSignatureIsRejected() {
            var token = jwt.generateToken("user1@test.invalid");
            ReflectionTestUtils.setField(jwt, "secret", SECRET + "different");
            assertThatThrownBy(() -> jwt.extractUsername(token)).isInstanceOf(JwtException.class);
        }
    }
    // SecurityUtils lấy cả ID và email từ UserPrincipal đang đăng nhập.
    @Test void securityUtilsReturnsCurrentIdentity() {
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(1);
        assertThat(SecurityUtils.getCurrentUserEmail()).isEqualTo("user1@test.invalid");
    }
    // UserPrincipal chuyển role toàn cục thành authorities cho Spring Security.
    @Test void principalExposesRoleAuthorities() {
        var u = Fixtures.user(1); var a = new UserRoleAssignment(); a.setRole(Fixtures.role(7, "developer"));
        u.setUserRoleAssignments(Set.of(a));
        assertThat(UserPrincipal.fromUser(u).getAuthorities()).extracting("authority").containsExactly("ROLE_DEVELOPER");
    }
}
