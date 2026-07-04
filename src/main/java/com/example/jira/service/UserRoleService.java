package com.example.jira.service;

import com.example.jira.dto.user_role.UserRoleRequest;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.UserRole;

import java.util.List;

public interface UserRoleService {
    List<UserRoleResponse> getAllUserRoles();
    UserRoleResponse createRole(UserRoleRequest request);
    UserRoleResponse updateRole(int roleId, UserRoleRequest request);
    void deleteRole(int roleId);
}
