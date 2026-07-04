package com.example.jira.service.impl;

import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.dto.permission.PermissionResponse;
import com.example.jira.mapper.PermissionMapper;
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
    private final PermissionRepository permissionRepository;

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

    @Override
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll()
                .stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    @Override
    public PermissionResponse createPermission(com.example.jira.dto.permission.PermissionRequest request) {
        if (permissionRepository.existsByPermissionName(request.getPermissionName())) {
            throw new RuntimeException("Quyền đã tồn tại!");
        }
        com.example.jira.entity.Permission permission = new com.example.jira.entity.Permission();
        permission.setPermissionName(request.getPermissionName());
        return PermissionMapper.toResponse(permissionRepository.save(permission));
    }

    @Override
    public PermissionResponse updatePermission(int permissionId, com.example.jira.dto.permission.PermissionRequest request) {
        com.example.jira.entity.Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quyền!"));
        
        permission.setPermissionName(request.getPermissionName());
        return PermissionMapper.toResponse(permissionRepository.save(permission));
    }

    @Override
    public void deletePermission(int permissionId) {
        com.example.jira.entity.Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quyền!"));
                
        if (rolePermissionRepository.existsByPermission_PermissionId(permissionId)) {
            throw new RuntimeException("Không thể xóa quyền này vì đang được sử dụng bởi một số vai trò!");
        }
        
        permissionRepository.delete(permission);
    }
}
