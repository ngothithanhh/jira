package com.example.jira.controller;

import com.example.jira.entity.UserRole;
import com.example.jira.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user-role")
@RequiredArgsConstructor
public class UserRoleController {
    private final UserRoleService userRoleService;

    @GetMapping
    public ResponseEntity<List<UserRole>> getAll(){
        return ResponseEntity.ok(userRoleService.getAllUserRoles());
    }

}
