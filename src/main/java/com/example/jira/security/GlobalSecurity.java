package com.example.jira.security;

import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;


@Component("globalSecurity")
@RequiredArgsConstructor
// Tên role là quan hệ lazy; giữ transaction đọc qua toàn bộ kiểm tra quyền, không phụ thuộc Open EntityManager In View.
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class GlobalSecurity {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;


    /**
     * Lấy ID users của người gọi từ phiên đã xác thực, không nhận ID người được cấp quyền từ request.
     * Thiếu Authentication thì dừng bằng 401 trước khi gọi SecurityUtils hoặc đọc DB.
     */
    public int currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Chưa đăng nhập");
        }
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * Dùng trước thao tác cấu hình chung hoặc khi xét quyền admin trên các dự án.
     * Chỉ role SYSTEM_ADMIN gán cho ID người gọi trong user_role_assignments mới là quản trị hệ thống.
     * Role cùng tên ở project_members hoặc role có một permission toàn cục không tạo quyền admin.
     * Chỉ đọc user_role_assignments/user_roles; không ghi DB và không cấp thêm role.
     */
    public boolean isSystemAdmin() {
        return isSystemAdmin(currentUserId());
    }

    boolean isSystemAdmin(int userId) {
        return userRoleAssignmentRepository.findByUser_UserId(userId).stream()
                .anyMatch(assignment -> assignment.getRole() != null
                        && "SYSTEM_ADMIN".equalsIgnoreCase(assignment.getRole().getRoleName()));
    }

    /** Chặn trước khi service đọc/ghi cấu hình toàn hệ thống; không phải admin thì dừng bằng 403. */
    public void assertSystemAdmin() {
        if (!isSystemAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền quản trị hệ thống");
        }
    }

    /**
     * Kiểm quyền toàn cục của người gọi cho các luồng cũ như tạo/xóa project.
     * CREATE_PROJECT cho mọi tài khoản đã xác thực; các quyền toàn hệ thống khác chỉ dành SYSTEM_ADMIN.
     * Assignment legacy của role dự án không cấp quyền toàn cục.
     * Quyền của các role đó được ProjectSecurity xét theo đúng project_members, không đọc tại đây.
     */
    public boolean hasPermission(PermissionName permission) {
        // Tạo project là khả năng chung của tài khoản đã xác thực; JWT filter/context kiểm tài khoản còn hoạt động.
        currentUserId();
        return permission == PermissionName.CREATE_PROJECT || isSystemAdmin();
    }
}
