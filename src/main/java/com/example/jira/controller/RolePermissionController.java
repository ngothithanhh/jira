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
