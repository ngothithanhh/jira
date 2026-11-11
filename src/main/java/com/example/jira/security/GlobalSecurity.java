package com.example.jira.security;

import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Component("globalSecurity")
@RequiredArgsConstructor
// Tên role là quan hệ lazy; giữ transaction đọc qua toàn bộ kiểm tra quyền, không phụ thuộc Open EntityManager In View.
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class GlobalSecurity {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;


    /**
     * Lấy ID users của người gọi từ phiên đã xác thực, không nhận ID người được cấp quyền từ request.
     * Thiếu Authentication thì dừng bằng 401 trước khi gọi SecurityUtils hoặc đọc DB.
     */
    public int currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Chưa đăng nhập");
        }
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * Dùng trước thao tác cấu hình chung hoặc khi xét quyền admin trên các dự án.
     * Chỉ role SYSTEM_ADMIN gán cho ID người gọi trong user_role_assignments mới là quản trị hệ thống.
     * Role cùng tên ở project_members hoặc role có một permission toàn cục không tạo quyền admin.
     * Chỉ đọc user_role_assignments/user_roles; không ghi DB và không cấp thêm role.
     */
    public boolean isSystemAdmin() {
        return userRoleAssignmentRepository.findByUser_UserId(currentUserId()).stream()
                .anyMatch(assignment -> assignment.getRole() != null
                        && "SYSTEM_ADMIN".equalsIgnoreCase(assignment.getRole().getRoleName()));
    }

    /** Chặn trước khi service đọc/ghi cấu hình toàn hệ thống; không phải admin thì dừng bằng 403. */
    public void assertSystemAdmin() {
        if (!isSystemAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền quản trị hệ thống");
        }
    }

    /**
     * Kiểm quyền toàn cục của người gọi cho các luồng cũ như tạo/xóa project.
     * Admin có quyền; các role khác chỉ có permission cụ thể trong role_permissions, không trở thành admin.
     */
    public boolean hasPermission(PermissionName permission) {
        int userId = currentUserId();

        List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findByUser_UserId(userId);

        if (assignments.isEmpty()) {
            return false;
        }

        for (UserRoleAssignment assignment : assignments) {
            // Role quản trị được gán toàn cục có quyền kể cả khi chưa seed đủ role_permissions.
            if ("SYSTEM_ADMIN".equalsIgnoreCase(assignment.getRole().getRoleName())) {
                return true;
            }
            int roleId = assignment.getRole().getRoleId();
            if (rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permission.name())) {
                return true;
            }
        }

        return false;
    }
}
