package com.example.jira.repository;

import com.example.jira.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {
    // Tất cả mutation gán admin/disable dùng khóa role chung trước khóa user, để không bỏ hai admin cuối đồng thời.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from UserRole r where upper(r.roleName) = 'SYSTEM_ADMIN' order by r.roleId")
    java.util.List<UserRole> lockAdminRoles();
    Optional<UserRole> findByRoleName(String roleName);
    Optional<UserRole> findByRoleId(int roleId);


    boolean existsByRoleName(String roleName);
}
