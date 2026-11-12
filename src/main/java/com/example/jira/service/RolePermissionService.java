package com.example.jira.service;

public interface RolePermissionService {
    com.example.jira.dto.admin.RolePermissionData.RoleSnapshot replacePermissions(int roleId,com.example.jira.dto.admin.RolePermissionData.ReplaceRequest request);
    void addPermissionToRole(int roleId, int permissionId);
    void removePermissionToRole(int roleId, int permissionId);

}
