package com.example.jira.mapper;

import com.example.jira.dto.permission.PermissionResponse;
import com.example.jira.entity.Permission;

public class PermissionMapper {

    public static PermissionResponse toResponse(Permission permission) {
        if (permission == null) {
            return null;
        }

        PermissionResponse response = new PermissionResponse();
        response.setPermissionId(permission.getPermissionId());
        response.setPermissionName(permission.getPermissionName());

        return response;
    }
}
