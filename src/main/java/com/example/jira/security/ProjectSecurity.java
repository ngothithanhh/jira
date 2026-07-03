package com.example.jira.security;

import com.example.jira.entity.ProjectMember;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import com.example.jira.entity.Project;
import com.example.jira.repository.ProjectRepository;

@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {

    private final ProjectMemberRepository projectMemberRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ProjectRepository projectRepository;
    private final GlobalSecurity globalSecurity;

    public boolean hasPermission(int projectId, PermissionName permission) {
        int userId = SecurityUtils.getCurrentUserId();

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project != null && project.getLead() != null && project.getLead().getUserId() == userId) {
            return true;
        }

        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId, userId);
        
        if (memberOpt.isEmpty()) {
            return false;
        }

        int roleId = memberOpt.get().getRole().getRoleId();

        return rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permission.name());
    }
}
