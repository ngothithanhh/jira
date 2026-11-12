package com.example.jira.controller;
import com.example.jira.dto.admin.*;
import com.example.jira.dto.admin.AdminUserData.*;
import com.example.jira.service.impl.AdminUserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserQueryService service;
    /** Đọc danh sách users đã lọc/phân trang; service kiểm SYSTEM_ADMIN trước truy vấn. */
    @GetMapping public UserPage search(@Valid @ModelAttribute AdminUserSearch request){return service.search(request);}
    /** userId là target trên URL, không thay actor JWT và không cấp quyền cho caller. */
    @GetMapping("/{userId}") public UserRow get(@PathVariable int userId){return service.get(userId);}
}
