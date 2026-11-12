package com.example.jira.service.impl;

import com.example.jira.dto.account.AccountContext;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.UserMapper;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountContextService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final GlobalSecurity global;
    private final ProjectSecurity projectSecurity;

    /**
     * Dùng khi FE dựng shell hoặc đổi project; actor lấy từ JWT, projectId là projects.project_id trong query.
     * Chỉ đọc users và quyền hiện có; không nhận userId tùy ý, không cấp role và không ghi DB.
     * Project thiếu trả 404; không được VIEW_PROJECT trả 403 trước khi trả quyền hay thông tin tài khoản.
     * Dùng lại hai lớp security đang bảo vệ API để menu FE không suy quyền từ tên role mẫu.
     */
    @Transactional(readOnly = true)
    public AccountContext getAccountContext(Integer projectId) {
        // Đọc người gọi đã xác thực; user bị vô hiệu hóa không được nhận context còn hiệu lực.
        var user = users.findById(global.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên không hợp lệ"));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản bị khóa");

        // Kiểm đúng tài nguyên trước khi liệt kê quyền; project không có không được coi là tập quyền rỗng.
        if (projectId != null) {
            if (projectId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID dự án không hợp lệ");
            if (!projects.existsById(projectId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dự án");
            projectSecurity.requirePermission(projectId, PermissionName.VIEW_PROJECT);
        }
        // Enum hiện có giới hạn số lần kiểm; toàn cục và quyền project được giữ thành hai tập riêng.
        List<String> globalPermissions = Arrays.stream(PermissionName.values()).filter(global::hasPermission)
                .map(Enum::name).toList();
        List<String> projectPermissions = projectId == null ? List.of() : Arrays.stream(PermissionName.values())
                .filter(permission -> projectSecurity.hasPermission(projectId, permission)).map(Enum::name).toList();
        return new AccountContext(UserMapper.toResponseFull(user), global.isSystemAdmin(), globalPermissions,
                projectId, projectPermissions);
    }
}
