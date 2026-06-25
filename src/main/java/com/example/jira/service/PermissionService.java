package com.example.jira.service;

public interface PermissionService {
    boolean checkPermission(int userId,String permissionName, int projectId);
}
