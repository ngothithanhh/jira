package com.example.jira.repository;

import com.example.jira.entity.RolePermission;
import com.example.jira.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    boolean existsByRole_RoleIdAndPermission_PermissionName(int roleId, String permissionName);
    boolean existsByRole_RoleIdAndPermission_PermissionId(int roleId, int permissionId);
    Optional<RolePermission> findByRole_RoleIdAndPermission_PermissionId(int roleId, int permissionId);


}
