package com.example.jira.security;

import com.example.jira.entity.ProjectMember;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

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

    /**
     * projectId là ID projects sở hữu dữ liệu, phải lấy từ board/issue khi client chỉ gửi ID con.
     * Admin toàn cục hoặc lead của đúng project có quyền; thành viên luôn được VIEW_PROJECT.
     * Quyền ghi của thành viên tra role_permissions theo role trong project_members của chính project/user.
     * Chỉ đọc DB; trả false khi không đủ quyền để caller dừng trước khi đọc nội dung hoặc ghi dữ liệu.
     */
    public boolean hasPermission(int projectId, PermissionName permission) {
        // Thiếu danh tính thì 401; ID người gọi lấy từ UserPrincipal, không lấy từ projectId.
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Chưa đăng nhập");
        }
        int userId = SecurityUtils.getCurrentUserId();

        // Chỉ SYSTEM_ADMIN trong user_role_assignments được vượt phạm vi dự án.
        return hasPermission(projectId, permission, userId, globalSecurity.isSystemAdmin());
    }

    /** Kiểm quyền của inviter lấy từ lời mời, không thay Authentication của recipient. */
    public boolean hasPermissionForUser(int projectId, PermissionName permission, int userId) {
        return hasPermission(projectId, permission, userId, globalSecurity.isSystemAdmin(userId));
    }

    private boolean hasPermission(int projectId, PermissionName permission, int userId, boolean systemAdmin) {
        if (systemAdmin) {
            return true;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project != null && project.getLead() != null && project.getLead().getUserId() == userId) {
            return true;
        }

        // Xóa project là quyền của chủ/lead và SYSTEM_ADMIN, không thể cấp cho thành viên bằng grant legacy.
        if (permission == PermissionName.DELETE_PROJECT) return false;

        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId, userId);
        
        if (memberOpt.isEmpty()) {
            return false;
        }

        // Membership đúng cặp project/user cho phép đọc kể cả role chưa có dòng VIEW_PROJECT riêng.
        if (permission == PermissionName.VIEW_PROJECT) {
            return true;
        }
        int roleId = memberOpt.get().getRole().getRoleId();

        return rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionName(roleId, permission.name());
    }

    /** Dùng với ID project sở hữu dữ liệu; thiếu quyền thì dừng bằng 403, không tự ghi DB. */
    public void requirePermission(int projectId, PermissionName permission) {
        if (!hasPermission(projectId, permission)) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện trên dự án này");
        }
    }

    /**
     * Dùng trước gán/gỡ/đổi issue type mặc định; projectId là ID projects từ path.
     * Lead/admin dùng lại hasPermission; thành viên cần MANAGE_BOARD hoặc UPDATE_PROJECT trên đúng project.
     * Không đủ quyền thì 403 trước mọi thay đổi project_issue_types; không cấp quyền cấu hình issue type toàn cục.
     */
    public void requireIssueTypeManagement(int projectId) {
        if (!hasPermission(projectId, PermissionName.MANAGE_BOARD)
                && !hasPermission(projectId, PermissionName.UPDATE_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền cấu hình loại nhiệm vụ của dự án này");
        }
    }
}
