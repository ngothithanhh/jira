package com.example.jira.security;

import com.example.jira.entity.ProjectMember;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {

    private final ProjectMemberRepository projectMemberRepository;
    private final RolePermissionRepository rolePermissionRepository;


    public boolean hasPermission(int projectId, String permissionName) {
        int userId = SecurityUtils.getCurrentUserId();

        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId, userId);
        
        if (memberOpt.isEmpty()) {
            return false;
        }

        int roleId = memberOpt.get().getRole().getRoleId();

        return rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permissionName);
    }
}
