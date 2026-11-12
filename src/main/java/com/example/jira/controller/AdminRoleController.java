package com.example.jira.controller;

import com.example.jira.dto.admin.AdminRoleData;
import com.example.jira.service.impl.AdminRoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/roles")
public class AdminRoleController {
    private final AdminRoleQueryService service;
    /** Trang quản trị đọc role/count; service kiểm SYSTEM_ADMIN trước mọi truy vấn. */
    @GetMapping public List<AdminRoleData> list(){return service.list();}
}
