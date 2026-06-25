package com.example.jira.repository;

import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.entity.UserRoleAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {
    List<UserRoleAssignment> findByUser_UserId(int userId);

    List<UserRoleAssignment> findByRole_RoleName(String roleName);

    boolean existsByUser_UserIdAndRole_RoleName(int userId, String roleName);

}
