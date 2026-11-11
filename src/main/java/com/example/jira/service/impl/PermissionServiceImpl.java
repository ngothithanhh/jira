package com.example.jira.service.impl;

import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.dto.permission.PermissionResponse;
import com.example.jira.mapper.PermissionMapper;
import com.example.jira.repository.*;
import com.example.jira.service.PermissionService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final GlobalSecurity globalSecurity;
    private final com.example.jira.security.ProjectSecurity projectSecurity;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Controller thành viên truyền ID người gọi đã xác thực, mã permission và ID projects từ path.
     * Không cho kiểm bằng ID người khác; projectSecurity xét lead/thành viên đúng dự án và admin toàn cục.
     * Chỉ trả boolean, không ghi DB; service addMember/removeMember vẫn chặn quyền trước mọi thay đổi.
     */
    @Override
    public boolean checkPermission(int userId, String permissionName, int projectId) {
        // ID user được kiểm phải trùng UserPrincipal; không mượn quyền của tài khoản khác.
        if (userId != globalSecurity.currentUserId()) {
            throw new AccessDeniedException("Không được kiểm quyền bằng danh tính người khác");
        }
        return projectSecurity.hasPermission(projectId, com.example.jira.enums.PermissionName.valueOf(permissionName));
    }

    @Override
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll()
                .stream()
                .map(PermissionMapper::toResponse)
                .toList();
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. Tạo mã quyền chung trong permissions từ request, không tự gán quyền cho người gọi.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public PermissionResponse createPermission(com.example.jira.dto.permission.PermissionRequest request) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        if (permissionRepository.existsByPermissionName(request.getPermissionName())) {
            throw new ConflictException("Quyền đã tồn tại!");
        }
        com.example.jira.entity.Permission permission = new com.example.jira.entity.Permission();
        permission.setPermissionName(request.getPermissionName());
        return PermissionMapper.toResponse(permissionRepository.save(permission));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. permissionId là ID permissions cần đổi mã, không phải ID của người gọi.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public PermissionResponse updatePermission(int permissionId, com.example.jira.dto.permission.PermissionRequest request) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        com.example.jira.entity.Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quyền!"));
        
        permission.setPermissionName(request.getPermissionName());
        return PermissionMapper.toResponse(permissionRepository.save(permission));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. permissionId là ID permissions cần xóa; chỉ xóa khi role_permissions không còn sử dụng.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void deletePermission(int permissionId) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        com.example.jira.entity.Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quyền!"));
                
        if (rolePermissionRepository.existsByPermission_PermissionId(permissionId)) {
            throw new ConflictException("Không thể xóa quyền này vì đang được sử dụng bởi một số vai trò!");
        }
        
        permissionRepository.delete(permission);
    }
}
