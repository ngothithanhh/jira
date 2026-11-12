package com.example.jira.repository;

import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.entity.UserRoleAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {
    /** Đọc role toàn cục theo nhóm user trong trang; projection không tải user/hash hoặc gây N truy vấn lazy. */
    @org.springframework.data.jpa.repository.Query("select a.user.userId, a.role.roleId, a.role.roleName from UserRoleAssignment a where a.user.userId in :ids order by a.user.userId, a.role.roleId")
    List<Object[]> adminRolesForUsers(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Integer> ids);
    // Đếm người, không đếm assignment: legacy có thể gán hai role khác hoa/thường cho cùng một admin.
    @org.springframework.data.jpa.repository.Query("select count(distinct a.user.userId) from UserRoleAssignment a where upper(a.role.roleName) = 'SYSTEM_ADMIN' and a.user.enabled = true")
    long countEnabledAdmins();
    @org.springframework.data.jpa.repository.Query("select (count(a)>0) from UserRoleAssignment a where a.user.userId = :userId and upper(a.role.roleName) = 'SYSTEM_ADMIN'")
    boolean isAdmin(@org.springframework.data.repository.query.Param("userId") int userId);
    List<UserRoleAssignment> findByUser_UserId(int userId);

    List<UserRoleAssignment> findByRole_RoleName(String roleName);

    boolean existsByUser_UserIdAndRole_RoleName(int userId, String roleName);

    boolean existsByRole_RoleId(int roleId);
}
