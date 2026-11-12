package com.example.jira.service.impl;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.service.*;
import com.example.jira.security.*;
import com.example.jira.enums.EntityType;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional
public class RolePermissionServiceImpl implements RolePermissionService {
    private final AdminRoleLock adminLock;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditLogService audit;

    /** API grant cũ: roleId thuộc user_roles, permissionId thuộc permissions; khóa/recheck actor và role trước kiểm phạm vi. */
    @Override public void addPermissionToRole(int roleId,int permissionId){
        adminLock.begin();var role=adminLock.target(roleId);
        var permission=permissionRepository.findById(permissionId).orElseThrow(()->new NotFoundException("Không tìm thấy quyền"));
        RolePolicy.projectGrant(role.getRoleName(),permission.getPermissionName());
        // Không tự chọn giữa các bản ghi permission alias; danh mục sai phải được kiểm tra trước cấp quyền.
        if(permissionRepository.findAll().stream().filter(p->RolePolicy.canonical(p.getPermissionName()).equals(permission.getPermissionName())).count()!=1)
            throw new ConflictException("Danh mục quyền có mã trùng");
        var id=new RolePermissionId(roleId,permissionId);
        if(rolePermissionRepository.existsById(id))throw new ConflictException("Quyền đã được gán");
        var grant=new RolePermission();grant.setId(id);grant.setRole(role);grant.setPermission(permission);
        rolePermissionRepository.saveAndFlush(grant);
        audit.logAction("GRANT_ROLE_PERMISSION",EntityType.ROLE,roleId,"Thêm quyền ID "+permissionId);
    }
    /** Gỡ đúng cặp kể cả grant legacy sai phạm vi; SYSTEM_ADMIN luôn toàn quyền nên không cho sửa cấu hình giả hiệu lực. */
    @Override public void removePermissionToRole(int roleId,int permissionId){
        adminLock.begin();var role=adminLock.target(roleId);
        if(RolePolicy.system(role.getRoleName()))throw new ConflictException("SYSTEM_ADMIN luôn toàn quyền");
        var grant=rolePermissionRepository.findByRole_RoleIdAndPermission_PermissionId(roleId,permissionId)
            .orElseThrow(()->new NotFoundException("Không tìm thấy quyền trong vai trò"));
        rolePermissionRepository.delete(grant);rolePermissionRepository.flush();
        audit.logAction("REVOKE_ROLE_PERMISSION",EntityType.ROLE,roleId,"Gỡ quyền ID "+permissionId);
    }
}
