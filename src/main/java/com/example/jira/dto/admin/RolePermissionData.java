package com.example.jira.dto.admin;

import java.util.List;

/** Snapshot và metadata quản trị, không thêm scope/version giả vào bảng role_permissions. */
public final class RolePermissionData {
    private RolePermissionData() {}
    public record PermissionMetadata(int permissionId,String permissionName,boolean supported,
                                     boolean assignableToProjectRole,boolean memberAlwaysAllowed) {}
    public record RoleSnapshot(int role,String roleName,List<Integer> permissionIds) {}
    public record MatrixRole(int role,String roleName,String scope,boolean reservedName,boolean editablePermissions,
                             long globalAssignmentCount,long projectAssignmentCount,List<Integer> permissionIds) {}
    public record Matrix(List<MatrixRole> roles,List<PermissionMetadata> permissions) {}
    public record ReplaceRequest(
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(contentUsing=PermissionIdDeserializer.class) List<Integer> expectedPermissionIds,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(contentUsing=PermissionIdDeserializer.class) List<Integer> permissionIds) {}
}
