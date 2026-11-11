package com.example.jira.service.impl;

import com.example.jira.entity.Permission;
import com.example.jira.entity.RolePermission;
import com.example.jira.entity.RolePermissionId;
import com.example.jira.entity.UserRole;
import com.example.jira.repository.PermissionRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.service.RolePermissionService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {
    private final GlobalSecurity globalSecurity;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. roleId thuộc user_roles, permissionId thuộc permissions; thêm đúng cặp vào role_permissions sau khi kiểm trùng.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void addPermissionToRole(int roleId, int permissionId){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        UserRole userRole = userRoleRepository.findByRoleId(roleId).orElseThrow(()->new NotFoundException("Không tìm thấy vai trò!"));

        Permission permission = permissionRepository.findById(permissionId).orElseThrow(()->new NotFoundException("Không tìm thấy quyền!"));

        RolePermissionId id = new RolePermissionId(roleId,permissionId);

        if(rolePermissionRepository.existsById(id)){
            throw new ConflictException("Quyền đã được gán!");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(id);
        rolePermission.setRole(userRole);
        rolePermission.setPermission(permission);

        rolePermissionRepository.save(rolePermission);
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. roleId/permissionId là cặp role_permissions cần gỡ, không xóa role hoặc permission gốc.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void removePermissionToRole(int roleId, int permissionId){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        
        RolePermission rolePermission = rolePermissionRepository.findByRole_RoleIdAndPermission_PermissionId(roleId, permissionId).orElseThrow(()->new NotFoundException("Không tìm thấy quyền trong vai trò!"));

        rolePermissionRepository.delete(rolePermission);

    }


}
