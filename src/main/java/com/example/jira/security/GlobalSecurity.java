package com.example.jira.security;

import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("globalSecurity")
@RequiredArgsConstructor
public class GlobalSecurity {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;


    public boolean hasPermission(PermissionName permission) {
        int userId = SecurityUtils.getCurrentUserId();

        List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findByUser_UserId(userId);

        if (assignments.isEmpty()) {
            return false;
        }

        for (UserRoleAssignment assignment : assignments) {
            int roleId = assignment.getRole().getRoleId();
            if (rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permission.name())) {
                return true;
            }
        }

        return false;
    }
}
