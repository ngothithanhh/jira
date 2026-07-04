package com.example.jira.mapper;

import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.UserRole;

public class UserRoleMapper {
    public static UserRoleResponse toResponse(UserRole role){
        return UserRoleResponse.builder()
                .role(role.getRoleId())
                .roleName(role.getRoleName())
                .build();
    }
}
