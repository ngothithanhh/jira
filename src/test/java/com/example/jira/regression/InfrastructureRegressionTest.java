package com.example.jira.regression;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.jira.config.DatabaseSeeder;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class InfrastructureRegressionTest {
    @Nested @ExtendWith(MockitoExtension.class)
    class CloudStorage {
        @Mock Cloudinary cloud;
        @Mock Uploader uploader;
        @InjectMocks CloudinaryServiceImpl service;
        // Adapter gửi byte file và resource_type auto, nhận URL HTTPS từ SDK giả lập.
        @Test void uploadUsesAutoResourceTypeAndSecureUrl() throws Exception {
            when(cloud.uploader()).thenReturn(uploader);
            when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "https://example.invalid/file"));
            var file = new MockMultipartFile("file", "file.txt", "text/plain", new byte[]{1, 2});
            assertThat(service.uploadFile(file)).isEqualTo("https://example.invalid/file");
            verify(uploader).upload(aryEq(new byte[]{1, 2}), argThat(m -> "auto".equals(m.get("resource_type"))));
        }
        // SDK upload lỗi phải được truyền lên service attachment để không lưu metadata giả.
        @Test void uploadPropagatesStorageFailure() throws Exception {
            when(cloud.uploader()).thenReturn(uploader);
            when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("unavailable"));
            assertThatThrownBy(() -> service.uploadFile(new MockMultipartFile("file", new byte[]{1}))).isInstanceOf(IOException.class);
        }
        // URL ảnh không có folder phải được chuyển thành public ID không gồm extension.
        @Test void deleteExtractsSimplePublicId() throws Exception {
            when(cloud.uploader()).thenReturn(uploader);
            service.deleteFile("https://example.invalid/image/upload/v1/sample.png");
            verify(uploader).destroy(eq("sample"), argThat(m -> "image".equals(m.get("resource_type"))));
        }
    }
    @Nested @ExtendWith(MockitoExtension.class)
    class Seeder {
        @Mock UserRepository users;
        @Mock UserRoleRepository roles;
        @Mock PermissionRepository permissions;
        @Mock RolePermissionRepository grants;
        @Mock UserRoleAssignmentRepository assignments;
        @Mock PasswordEncoder encoder;
        @InjectMocks DatabaseSeeder seeder;
        // Dữ liệu đã có: seeder không tạo lại role, permission, grant hoặc admin.
        @Test void existingSeedDataDoesNotCreateDuplicates() {
            when(permissions.findByPermissionName(anyString())).thenAnswer(inv -> {
                String name = inv.getArgument(0);
                if (Set.of("EDIT_PROJECT", "REMOVE_MEMBER", "MANAGE_USERS").contains(name)) return Optional.empty();
                var p = new Permission(); p.setPermissionName(name); return Optional.of(p);
            });
            when(roles.findByRoleName(anyString())).thenAnswer(inv -> Optional.of(Fixtures.role(7, inv.getArgument(0))));
            lenient().when(grants.existsByRole_RoleIdAndPermission_PermissionId(anyInt(), anyInt())).thenReturn(true);
            when(users.existsByEmail("admin@jira.com")).thenReturn(true);
            seeder.run();
            verify(roles, never()).save(any()); verify(permissions, never()).save(any());
            verify(grants, never()).save(any()); verify(users, never()).save(any()); verifyNoInteractions(assignments, encoder);
        }
        // Permission cũ chỉ có tên legacy phải được đổi tên, không tạo bản ghi cùng nghĩa.
        @Test void migratesLegacyPermissionInPlace() {
            var legacy = new Permission(); legacy.setPermissionId(8); legacy.setPermissionName("EDIT_PROJECT");
            when(permissions.findByPermissionName("EDIT_PROJECT")).thenReturn(Optional.of(legacy));
            // Lượt tra sau save phải thấy permission đã đổi tên như repository thật.
            when(permissions.findByPermissionName("UPDATE_PROJECT")).thenReturn(Optional.empty(), Optional.of(legacy));
            when(permissions.save(any())).thenAnswer(i -> i.getArgument(0));
            when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
            when(users.existsByEmail("admin@jira.com")).thenReturn(true);
            seeder.run(); assertThat(legacy.getPermissionName()).isEqualTo("UPDATE_PROJECT"); verify(permissions).save(legacy);
            verify(permissions, times(1)).save(argThat(p -> "UPDATE_PROJECT".equals(p.getPermissionName())));
            verify(permissions, never()).delete(legacy);
        }
        // Cả tên cũ và mới tồn tại: chuyển grant sang canonical rồi mới bỏ permission legacy.
        @Test void mergesLegacyGrantsIntoExistingCanonicalPermission() {
            var legacy = new Permission(); legacy.setPermissionId(8); legacy.setPermissionName("EDIT_PROJECT");
            var canonical = new Permission(); canonical.setPermissionId(9); canonical.setPermissionName("UPDATE_PROJECT");
            var role = Fixtures.role(7, "PROJECT_MANAGER");
            var oldGrant = new RolePermission(); oldGrant.setRole(role); oldGrant.setPermission(legacy);
            when(permissions.findByPermissionName("EDIT_PROJECT")).thenReturn(Optional.of(legacy));
            when(permissions.findByPermissionName("UPDATE_PROJECT")).thenReturn(Optional.of(canonical));
            when(grants.findByPermission_PermissionId(8)).thenReturn(List.of(oldGrant));
            when(permissions.save(any())).thenAnswer(i -> i.getArgument(0));
            when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
            when(users.existsByEmail("admin@jira.com")).thenReturn(true);
            seeder.run();
            var order = inOrder(grants, permissions);
            order.verify(grants).save(argThat(g -> g.getRole() == role && g.getPermission() == canonical));
            order.verify(grants).deleteAll(List.of(oldGrant)); order.verify(permissions).delete(legacy);
            verify(permissions, never()).delete(canonical);
        }
    }
}
