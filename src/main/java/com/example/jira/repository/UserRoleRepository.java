package com.example.jira.repository;

import com.example.jira.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {
    Optional<UserRole> findByRoleName(String roleName);
    Optional<UserRole> findByRoleId(int roleId);


    boolean existsByRoleName(String roleName);
}
