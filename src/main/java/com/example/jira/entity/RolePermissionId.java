package com.example.jira.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RolePermissionId implements Serializable {
    private int roleId;
    private int permissionId;

    public RolePermissionId() {
    }

    public RolePermissionId(int roleId, int permissionId){
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public int getRoleId() {
        return roleId;
    }

    public void setRoleId(int roleId) {
        this.roleId = roleId;
    }

    public int getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(int permissionId) {
        this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RolePermissionId that)) return false;
        return roleId == that.roleId && permissionId == that.permissionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, permissionId);
    }
}
