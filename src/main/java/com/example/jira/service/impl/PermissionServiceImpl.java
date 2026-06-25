package com.example.jira.service.impl;

import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.repository.*;
import com.example.jira.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public boolean checkPermission(int userId,String permissionName, int projectId){
        List<UserRoleAssignment> globalRoles = userRoleAssignmentRepository.findByUser_UserId(userId);

        for(UserRoleAssignment ura:globalRoles){
            int roleId = ura.getRole().getRoleId();

            if(rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId,permissionName)){
                return true;
            }
        }

        ProjectMember member = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId,userId).orElse(null);
        if(member != null){
            int roleId = member.getRole().getRoleId();

            return rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId,permissionName);
        }
        return false;

    }
}
