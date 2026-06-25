package com.example.jira.service;

public interface RolePermissionService {
    void addPermissionToRole(int roleId, int permissionId);
    void removePermissionToRole(int roleId, int permissionId);

}
