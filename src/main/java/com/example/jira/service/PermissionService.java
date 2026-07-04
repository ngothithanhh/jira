package com.example.jira.service;

import com.example.jira.dto.permission.PermissionRequest;
import com.example.jira.dto.permission.PermissionResponse;
import java.util.List;

public interface PermissionService {
    boolean checkPermission(int userId,String permissionName, int projectId);
    List<PermissionResponse> getAllPermissions();
    PermissionResponse createPermission(PermissionRequest request);
    PermissionResponse updatePermission(int permissionId, PermissionRequest request);
    void deletePermission(int permissionId);
}
