package com.example.jira.service.impl;

import com.example.jira.dto.user_role.UserRoleRequest;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.UserRole;
import com.example.jira.mapper.UserRoleMapper;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.service.UserRoleService;
import com.example.jira.service.UserService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {
    private final GlobalSecurity globalSecurity;

    private final UserRoleRepository userRoleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public List<UserRoleResponse> getAllUserRoles(){
        return userRoleRepository.findAll()
                .stream().map(UserRoleMapper::toResponse).toList();
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. Tạo user_roles từ tên role trong request; không tạo user_role_assignments cho người gọi.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public UserRoleResponse createRole(UserRoleRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();

        // SYSTEM_ADMIN được seeder tạo và dùng làm khóa quản trị; không tạo bản sao khác chữ hoa/thường qua API.
        if ("SYSTEM_ADMIN".equalsIgnoreCase(request.getRoleName())) throw new ConflictException("SYSTEM_ADMIN là vai trò dành riêng");
        if(userRoleRepository.existsByRoleName(request.getRoleName()))
            throw new ConflictException("Vai trò đã tồn tại!");

        UserRole userRole = new UserRole();
        userRole.setRoleName(request.getRoleName());

        return UserRoleMapper.toResponse(userRoleRepository.save(userRole));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. roleId là ID user_roles cần đổi tên, không phải role của người đang đăng nhập.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public UserRoleResponse updateRole(int roleId, UserRoleRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        UserRole userRole = userRoleRepository.findByRoleId(roleId).orElseThrow(()->new NotFoundException("Không tìm thấy vai trò!"));

        // Không đổi vai trò khác thành admin hoặc đổi mất tên admin đang bảo vệ last-admin/khóa quản trị.
        if ("SYSTEM_ADMIN".equalsIgnoreCase(userRole.getRoleName()) || "SYSTEM_ADMIN".equalsIgnoreCase(request.getRoleName())) throw new ConflictException("Không đổi tên vai trò SYSTEM_ADMIN");
        userRole.setRoleName(request.getRoleName());

        return UserRoleMapper.toResponse(userRoleRepository.save(userRole));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. roleId là ID user_roles cần xóa; giữ lại kiểm tra tham chiếu toàn cục và project trước khi xóa.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void deleteRole(int roleId){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        if (userRoleRepository.findByRoleId(roleId).filter(role -> "SYSTEM_ADMIN".equalsIgnoreCase(role.getRoleName())).isPresent()) throw new ConflictException("Không xóa vai trò SYSTEM_ADMIN");
        if (userRoleAssignmentRepository.existsByRole_RoleId(roleId) || projectMemberRepository.existsByRole_RoleId(roleId)) {
            throw new ConflictException("Không thể xóa vai trò này vì đang được gán cho người dùng hoặc dự án!");
        }
        
        userRoleRepository.deleteById(roleId);
    }
}
