package com.example.jira.repository;

import com.example.jira.entity.RolePermission;
import com.example.jira.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    @org.springframework.data.jpa.repository.Query("select rp.role.roleId,rp.permission.permissionId from RolePermission rp order by rp.role.roleId,rp.permission.permissionId")
    List<Object[]> matrixPairs();
    @org.springframework.data.jpa.repository.Query("select rp.permission.permissionId from RolePermission rp where rp.role.roleId=:id order by rp.permission.permissionId")
    List<Integer> permissionIds(@org.springframework.data.repository.query.Param("id") int id);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RolePermission rp where rp.role.roleId=:roleId and rp.permission.permissionId in :ids")
    int deleteSelected(@org.springframework.data.repository.query.Param("roleId") int roleId,@org.springframework.data.repository.query.Param("ids") java.util.Collection<Integer> ids);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RolePermission rp where rp.role.roleId=:id")
    int deleteForRole(@org.springframework.data.repository.query.Param("id") int id);
    boolean existsByRole_RoleIdAndPermission_PermissionName(int roleId, String permissionName);
    boolean existsByRole_RoleIdAndPermission_PermissionId(int roleId, int permissionId);
    Optional<RolePermission> findByRole_RoleIdAndPermission_PermissionId(int roleId, int permissionId);
    boolean existsByPermission_PermissionId(int permissionId);
    List<RolePermission> findByPermission_PermissionId(int permissionId);

}
