package com.example.jira.service.impl;

import com.example.jira.dto.user_role.*;
import com.example.jira.entity.UserRole;
import com.example.jira.mapper.UserRoleMapper;
import com.example.jira.repository.*;
import com.example.jira.service.*;
import com.example.jira.security.*;
import com.example.jira.enums.EntityType;
import com.example.jira.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class UserRoleServiceImpl implements UserRoleService {
    private final AdminRoleLock adminLock;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final RolePermissionRepository grants;
    private final AuditLogService audit;

    /** Danh mục cũ giữ nguyên DTO role/roleName cho trang thành viên; chỉ đọc, không thay tên legacy. */
    @Override @Transactional(readOnly=true)
    public List<UserRoleResponse> getAllUserRoles(){
        return userRoleRepository.findAll().stream().map(UserRoleMapper::toResponse).toList();
    }

    /** Admin tạo mã role dự án; khóa/recheck actor rồi chuẩn hóa, kiểm trùng trước lưu role và audit cùng transaction. */
    @Override public UserRoleResponse createRole(UserRoleRequest request){
        adminLock.begin();
        String name=RolePolicy.roleName(request==null?null:request.getRoleName());
        checkName(name,0);
        var role=new UserRole();role.setRoleName(name);
        role=userRoleRepository.saveAndFlush(role);
        audit.logAction("CREATE_ROLE",EntityType.ROLE,role.getRoleId(),"Tạo vai trò "+name);
        return UserRoleMapper.toResponse(role);
    }

    /** roleId từ hàng user_roles đã chọn; đổi tên custom giữ nguyên ID, membership và quyền, không gán lại người dùng. */
    @Override public UserRoleResponse updateRole(int roleId,UserRoleRequest request){
        adminLock.begin();
        var role=adminLock.target(roleId);
        // Giữ bốn tên seed; chặn cả tên nguồn và tên đích để không biến custom thành role được guard đặc biệt.
        if(RolePolicy.reserved(role.getRoleName()))throw new ConflictException("Không đổi tên vai trò mặc định");
        String name=RolePolicy.roleName(request==null?null:request.getRoleName());
        checkName(name,roleId);
        // Cùng tên chuẩn hiện có là no-op, không lưu hoặc tạo audit thay đổi giả.
        if(name.equals(role.getRoleName()))return UserRoleMapper.toResponse(role);
        String previous=role.getRoleName();role.setRoleName(name);
        role=userRoleRepository.saveAndFlush(role);
        audit.logAction("UPDATE_ROLE",EntityType.ROLE,roleId,"Đổi vai trò "+previous+" thành "+name);
        return UserRoleMapper.toResponse(role);
    }

    /**
     * Xóa custom chưa gán cho ai: khóa actor/role, kiểm hai bảng tham chiếu rồi xóa grants của chính role đó.
     * FK vẫn chặn membership được gán xen kẽ; lỗi flush rollback cả grants, role và audit, không cascade người dùng.
     */
    @Override public void deleteRole(int roleId){
        adminLock.begin();
        var role=adminLock.target(roleId);
        if(RolePolicy.reserved(role.getRoleName()))throw new ConflictException("Không xóa vai trò mặc định");
        if(userRoleAssignmentRepository.existsByRole_RoleId(roleId)||projectMemberRepository.existsByRole_RoleId(roleId))
            throw new ConflictException("Vai trò đang được gán cho người dùng hoặc dự án");
        grants.deleteForRole(roleId);
        userRoleRepository.delete(role);userRoleRepository.flush();
        audit.logAction("DELETE_ROLE",EntityType.ROLE,roleId,"Xóa vai trò "+role.getRoleName());
    }

    /** Tên canonical của role khác đã tồn tại hoặc là tên seed thì409; không tự sửa dữ liệu legacy. */
    private void checkName(String name,int excluded){
        if(RolePolicy.reserved(name))throw new ConflictException("Tên vai trò mặc định được dành riêng");
        if(userRoleRepository.canonicalExists(name,excluded))throw new ConflictException("Vai trò đã tồn tại");
    }
}
