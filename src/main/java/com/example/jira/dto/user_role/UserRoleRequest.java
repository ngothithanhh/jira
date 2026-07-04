package com.example.jira.dto.user_role;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRoleRequest {
    @NotBlank(message = "Tên vai trò không được để trống")
    private String roleName;
}
