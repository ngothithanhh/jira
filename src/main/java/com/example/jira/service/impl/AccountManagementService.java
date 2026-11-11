package com.example.jira.service.impl;

import com.example.jira.dto.account.AccountRequests.*;
import com.example.jira.dto.user.UserResponse;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.EntityType;
import com.example.jira.mapper.*;
import com.example.jira.repository.*;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.service.AuditLogService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountManagementService {
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private final RefreshTokenRepository tokens;
    private final GlobalSecurity global;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;
    private final AuditLogService audit;

    /** ID users lấy từ danh tính hiện tại; khóa/refresh rồi sửa tên, không nhận ID người khác, email, role hoặc hash. */
    @Transactional
    public UserResponse profile(Profile request) {
        if (request == null || (request.userName()!=null && (request.userName().isBlank() || request.userName().length()>50))
                || (request.fullName()!=null && request.fullName().length()>100)) throw error(HttpStatus.BAD_REQUEST,"Tên không hợp lệ");
        User user = currentUser();
        if (request.userName()!=null) user.setUserName(request.userName().trim());
        if (request.fullName()!=null) user.setFullName(request.fullName());
        users.save(user); audit.logAction("UPDATE_PROFILE",EntityType.USER,user.getUserId(),"Cập nhật hồ sơ cá nhân");
        return UserMapper.toResponseFull(user);
    }

    /**
     * Đổi mật khẩu của users trong danh tính hiện tại; khóa user trước so hash/thu hồi token.
     * Đúng mật khẩu cũ mới ghi hash mới, tăng auth_version và bulk revoke refresh cùng audit/transaction.
     * Lỗi rollback cả hash/version/phiên; response không chứa mật khẩu, hash hay token.
     */
    @Transactional
    public void password(Password request) {
        if (request==null || request.oldPassword()==null || request.oldPassword().isBlank() || request.oldPassword().getBytes(StandardCharsets.UTF_8).length>72
                || request.newPassword()==null || request.newPassword().isBlank() || request.newPassword().length()<8 || request.newPassword().getBytes(StandardCharsets.UTF_8).length>72) {
            throw error(HttpStatus.BAD_REQUEST,"Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8");
        }
        User user = currentUser();
        if (!encoder.matches(request.oldPassword(),user.getPasswordHash())) throw error(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại không đúng");
        user.setPasswordHash(encoder.encode(request.newPassword())); bumpVersion(user);
        users.save(user); tokens.revokeAllForUser(user.getUserId(),LocalDateTime.now());
        audit.logAction("CHANGE_PASSWORD",EntityType.USER,user.getUserId(),"Đổi mật khẩu và thu hồi phiên");
    }

    /** Chỉ admin toàn cục đọc role toàn cục của target users; role project nằm ở API thành viên, không ở danh sách này. */
    @Transactional(readOnly=true)
    public List<UserRoleResponse> roles(int userId) {
        global.assertSystemAdmin(); users.findById(userId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy người dùng"));
        return assignments.findByUser_UserId(userId).stream().map(assignment -> UserRoleMapper.toResponse(assignment.getRole())).toList();
    }

    /** roleId là user_roles gán cho target users trong user_role_assignments; khóa admin chung rồi user, cặp đã có là no-op. */
    @Transactional
    public void grantRole(int userId,int roleId) {
        User target = adminTarget(userId); UserRole role = role(roleId); var id = new UserRoleAssignmentId(userId,roleId);
        if (assignments.existsById(id)) return;
        var assignment = new UserRoleAssignment(); assignment.setId(id); assignment.setUser(target); assignment.setRole(role); assignment.setAssignedAt(LocalDateTime.now()); assignments.save(assignment);
        audit.logAction("GRANT_ROLE",EntityType.USER,userId,"Gán role ID " + roleId);
    }

    /** Thu hồi đúng cặp user/role; admin đang enabled cuối cùng phải giữ lại, 409 trước ghi, không tự gỡ role project. */
    @Transactional
    public void revokeRole(int userId,int roleId) {
        User target = adminTarget(userId); UserRole role = role(roleId); var id = new UserRoleAssignmentId(userId,roleId);
        if (!assignments.existsById(id)) return;
        if (target.isEnabled() && "SYSTEM_ADMIN".equalsIgnoreCase(role.getRoleName()) && assignments.countEnabledAdmins()<=1) {
            throw error(HttpStatus.CONFLICT,"Phải giữ ít nhất một quản trị viên đang hoạt động");
        }
        assignments.deleteById(id); audit.logAction("REVOKE_ROLE",EntityType.USER,userId,"Thu hồi role ID " + roleId);
    }

    /** Đổi enabled của target dưới khóa admin/user; giữ admin cuối, tăng version và thu hồi refresh cả khi mở lại tài khoản. */
    @Transactional
    public void enabled(int userId,Boolean enabled) {
        if (enabled==null) throw error(HttpStatus.BAD_REQUEST,"Thiếu trạng thái tài khoản");
        User target = adminTarget(userId);
        if (target.isEnabled()==enabled) return;
        if (!enabled && assignments.isAdmin(userId) && assignments.countEnabledAdmins()<=1) throw error(HttpStatus.CONFLICT,"Phải giữ ít nhất một quản trị viên đang hoạt động");
        target.setEnabled(enabled); bumpVersion(target); users.save(target); tokens.revokeAllForUser(userId,LocalDateTime.now());
        audit.logAction(enabled?"ENABLE_USER":"DISABLE_USER",EntityType.USER,userId,"Đổi trạng thái tài khoản");
    }

    /** Endpoint xóa user cũ đi qua cùng khóa admin; phải gỡ global roles trước, dữ liệu FK đang dùng sẽ rollback/409. */
    @Transactional
    public void deleteUser(int userId) {
        User target = adminTarget(userId);
        if (!assignments.findByUser_UserId(userId).isEmpty()) throw error(HttpStatus.CONFLICT,"Cần gỡ role toàn cục trước khi xóa tài khoản");
        users.delete(target); entityManager.flush(); audit.logAction("DELETE_USER",EntityType.USER,userId,"Xóa tài khoản");
    }

    /** Khóa role SYSTEM_ADMIN làm điểm tuần tự hóa, rồi refresh actor/recheck quyền trước khóa target; thiếu admin trả 403. */
    private User adminTarget(int userId) {
        global.assertSystemAdmin();
        if (roles.lockAdminRoles().isEmpty()) throw error(HttpStatus.CONFLICT,"Chưa có cấu hình SYSTEM_ADMIN");
        User actor = lockedUser(global.currentUserId());
        if (!actor.isEnabled()) throw new AccessDeniedException("Tài khoản quản trị đã bị khóa");
        global.assertSystemAdmin();
        return actor.getUserId()==userId?actor:lockedUser(userId);
    }
    /** Khóa đúng user trong phiên, refresh để thấy disabled/auth_version vừa commit, không mượn target ID từ body. */
    private User currentUser() {
        User user = lockedUser(global.currentUserId());
        if (!user.isEnabled()) throw error(HttpStatus.UNAUTHORIZED,"Tài khoản đã bị khóa");
        return user;
    }
    /** ID bảng users từ actor/target đã xác định; chỉ khóa/refresh, caller tự ghi trong transaction hiện tại. */
    private User lockedUser(int id) {
        User user = users.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy người dùng"));
        entityManager.refresh(user); return user;
    }
    /** Tra ID user_roles, thiếu 404 trước khi lưu assignment. */
    private UserRole role(int id) { return roles.findByRoleId(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy vai trò")); }
    /** Không để counter tràn thành số âm; version tăng làm JWT cũ bị filter từ chối ở request sau. */
    private void bumpVersion(User user) {
        if (user.getAuthVersion()==Long.MAX_VALUE) throw error(HttpStatus.CONFLICT,"Không thể tăng phiên bản xác thực");
        user.setAuthVersion(user.getAuthVersion()+1);
    }
    /** Lỗi nghiệp vụ có HTTP status; không ghi secret vào message/log. */
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
