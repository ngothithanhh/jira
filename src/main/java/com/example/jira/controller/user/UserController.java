package com.example.jira.controller.user;

import com.example.jira.dto.user.UserResponse;
import com.example.jira.entity.UserRole;
import com.example.jira.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll(){
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
