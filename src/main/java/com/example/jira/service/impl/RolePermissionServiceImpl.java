package com.example.jira.service.impl;

import com.example.jira.entity.Permission;
import com.example.jira.entity.RolePermission;
import com.example.jira.entity.RolePermissionId;
import com.example.jira.entity.UserRole;
import com.example.jira.repository.PermissionRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public void addPermissionToRole(int roleId, int permissionId){
        UserRole userRole = userRoleRepository.findByRoleId(roleId).orElseThrow(()->new RuntimeException("Không tìm thấy vai trò!"));

        Permission permission = permissionRepository.findById(permissionId).orElseThrow(()->new RuntimeException("Không tìm thấy quyền!"));

        RolePermissionId id = new RolePermissionId(roleId,permissionId);

        if(rolePermissionRepository.existsById(id)){
            throw new RuntimeException("Quyền đã được gán!");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(id);
        rolePermission.setRole(userRole);
        rolePermission.setPermission(permission);

        rolePermissionRepository.save(rolePermission);
    }

    @Override
    public void removePermissionToRole(int roleId, int permissionId){
        
        RolePermission rolePermission = rolePermissionRepository.findByRole_RoleIdAndPermission_PermissionId(roleId, permissionId).orElseThrow(()->new RuntimeException("Không tìm thấy quyền trong vai trò!"));

        rolePermissionRepository.delete(rolePermission);

    }


}
