package com.example.jira.controller;

import com.example.jira.entity.UserRole;
import com.example.jira.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import com.example.jira.dto.user_role.UserRoleRequest;
import com.example.jira.dto.user_role.UserRoleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RestController
@RequestMapping("/api/user-role")
@RequiredArgsConstructor
public class UserRoleController {
    private final UserRoleService userRoleService;

    @GetMapping
    public ResponseEntity<List<UserRoleResponse>> getAll(){
        return ResponseEntity.ok(userRoleService.getAllUserRoles());
    }

    @PostMapping
    public ResponseEntity<UserRoleResponse> createRole(@RequestBody UserRoleRequest request) {
        UserRoleResponse response = userRoleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<UserRoleResponse> updateRole(@PathVariable int roleId, @RequestBody UserRoleRequest request) {
        UserRoleResponse response = userRoleService.updateRole(roleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable int roleId) {
        userRoleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

}
