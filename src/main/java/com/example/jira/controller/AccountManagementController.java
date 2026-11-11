package com.example.jira.controller;
import com.example.jira.dto.account.AccountRequests.*;
import com.example.jira.dto.user.UserResponse;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.service.impl.AccountManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
public class AccountManagementController {
    private final AccountManagementService service;
    /** Hồ sơ luôn thuộc user trong token, không có target userId ở route. */
    @PatchMapping("/api/account/profile") public UserResponse profile(@Valid @RequestBody Profile body) { return service.profile(body); }
    /** Thành công thu hồi phiên, client cần đăng nhập lại với mật khẩu mới. */
    @PostMapping("/api/account/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody Password body) { service.password(body); }
    /** Role toàn cục khác role project; service kiểm SYSTEM_ADMIN trước đọc. */
    @GetMapping("/api/admin/users/{userId}/roles") public List<UserRoleResponse> roles(@PathVariable int userId) { return service.roles(userId); }
    /** Gán cặp user/role toàn cục, idempotent khi đã có. */
    @PutMapping("/api/admin/users/{userId}/roles/{roleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable int userId,@PathVariable int roleId) { service.grantRole(userId,roleId); }
    /** Giữ ít nhất một admin enabled, không gỡ role project. */
    @DeleteMapping("/api/admin/users/{userId}/roles/{roleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable int userId,@PathVariable int roleId) { service.revokeRole(userId,roleId); }
    /** Đổi trạng thái tài khoản và vô hiệu hóa token cũ cùng transaction. */
    @PatchMapping("/api/admin/users/{userId}/enabled") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enabled(@PathVariable int userId,@Valid @RequestBody Enabled body) { service.enabled(userId,body.enabled()); }
}
