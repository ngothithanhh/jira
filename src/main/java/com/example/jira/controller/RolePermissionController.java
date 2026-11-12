package com.example.jira.controller;

import com.example.jira.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles/{roleId}/permissions")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final com.example.jira.service.impl.AdminRolePermissionQueryService query;

    /** roleId thuộc user_roles được chọn; đọc stored set, không suy quyền từ tên role. */
    @GetMapping public com.example.jira.dto.admin.RolePermissionData.RoleSnapshot get(@PathVariable int roleId){return query.snapshot(roleId);}

    /** Lưu đúng một role nguyên tử; expected là snapshot cũ, service kiểm quyền/khóa/stale trước ghi. */
    @PutMapping public com.example.jira.dto.admin.RolePermissionData.RoleSnapshot replace(@PathVariable int roleId,
        @RequestBody com.example.jira.dto.admin.RolePermissionData.ReplaceRequest request){
        return rolePermissionService.replacePermissions(roleId,request);
    }

    @PostMapping
    public ResponseEntity<Void> addPermissionToRole(
            @PathVariable int roleId,
            @RequestParam int permissionId) {
        
        rolePermissionService.addPermissionToRole(roleId, permissionId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removePermissionFromRole(
            @PathVariable int roleId,
            @RequestParam int permissionId) {
        
        rolePermissionService.removePermissionToRole(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
