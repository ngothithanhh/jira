package com.example.jira.controller;

import com.example.jira.dto.admin.RolePermissionData.Matrix;
import com.example.jira.service.impl.AdminRolePermissionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/role-permissions")
public class AdminRolePermissionController {
    private final AdminRolePermissionQueryService query;
    /** Snapshot nhiều bảng chỉ dành SYSTEM_ADMIN; service kiểm quyền trước đọc danh mục. */
    @GetMapping public Matrix matrix(){return query.matrix();}
}
