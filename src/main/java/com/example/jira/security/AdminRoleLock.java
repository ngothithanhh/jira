package com.example.jira.security;

import com.example.jira.entity.UserRole;
import com.example.jira.repository.*;
import com.example.jira.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AdminRoleLock {
    private final GlobalSecurity global;
    private final UserRoleRepository roles;
    private final UserRepository users;
    private final EntityManager entityManager;

    /**
     * Dùng trong transaction ghi role/quyền: kiểm actor JWT trước, khóa role admin rồi khóa/refresh users của actor.
     * Cùng thứ tự AccountManagementService để không ghi tiếp bằng quyền admin đã bị thu hồi lúc chờ khóa.
     * Hàm chỉ khóa và đọc; caller thực hiện thay đổi/audit trong cùng transaction.
     */
    public void begin() {
        global.assertSystemAdmin();
        if(roles.lockAdminRoles().isEmpty())throw new ConflictException("Chưa có cấu hình SYSTEM_ADMIN");
        var actor=users.findByIdForUpdate(global.currentUserId()).orElseThrow(()->new NotFoundException("Không tìm thấy người dùng"));
        entityManager.refresh(actor);
        if(!actor.isEnabled())throw new AccessDeniedException("Tài khoản quản trị đã bị khóa");
        global.assertSystemAdmin();
    }

    /** roleId thuộc user_roles cần sửa/xóa, không phải userId; gọi sau begin, thiếu404, refresh sau chờ khóa. */
    public UserRole target(int roleId) {
        var role=roles.lockById(roleId).orElseThrow(()->new NotFoundException("Không tìm thấy vai trò"));
        entityManager.refresh(role);
        return role;
    }
}
