package com.example.jira.repository;

import com.example.jira.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from UserRole r where r.roleId = :id")
    Optional<UserRole> lockById(@org.springframework.data.repository.query.Param("id") int id);
    @org.springframework.data.jpa.repository.Query("select (count(r)>0) from UserRole r where upper(trim(r.roleName))=:name and r.roleId<>:excluded")
    boolean canonicalExists(@org.springframework.data.repository.query.Param("name") String name,@org.springframework.data.repository.query.Param("excluded") int excluded);
    // Tất cả mutation gán admin/disable dùng khóa role chung trước khóa user, để không bỏ hai admin cuối đồng thời.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from UserRole r where upper(r.roleName) = 'SYSTEM_ADMIN' order by r.roleId")
    java.util.List<UserRole> lockAdminRoles();
    Optional<UserRole> findByRoleName(String roleName);
    Optional<UserRole> findByRoleId(int roleId);


    boolean existsByRoleName(String roleName);
}
