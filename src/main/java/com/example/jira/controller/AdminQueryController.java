package com.example.jira.controller;
import com.example.jira.dto.audit.*;
import com.example.jira.dto.audit.AdminData.*;
import com.example.jira.service.impl.AdminQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/admin")
public class AdminQueryController {
    private final AdminQueryService service;
    /** Service kiểm admin trước đọc audit, mỗi row DTO giữ actor nullable cho hành động hệ thống. */
    @GetMapping("/audit-logs") public AuditPage audit(@Valid @ModelAttribute AuditSearchRequest request) { return service.audit(request); }
    /** Tổng quan quản trị không trả dữ liệu riêng của từng user/project. */
    @GetMapping("/overview") public Overview overview() { return service.overview(); }
}
