package com.example.jira.security;

import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("globalSecurity")
@RequiredArgsConstructor
public class GlobalSecurity {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Hàm này kiểm tra xem User hiện tại có Permission Toàn Hệ Thống (Global) hay không.
     * Sử dụng cho các thao tác như Create Project.
     */
    public boolean hasPermission(String permissionName) {
        int userId = SecurityUtils.getCurrentUserId();

        // 1. Lấy tất cả các Global Role được gán cho user này
        List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findByUser_UserId(userId);

        if (assignments.isEmpty()) {
            return false;
        }

        // 2. Lặp qua các Role xem có Role nào chứa permission truyền vào không
        for (UserRoleAssignment assignment : assignments) {
            int roleId = assignment.getRole().getRoleId();
            if (rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permissionName)) {
                return true; // Chỉ cần 1 role có quyền là đủ
            }
        }

        return false;
    }
}
