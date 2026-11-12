package com.example.jira.repository;

import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
    @org.springframework.data.jpa.repository.Query("select m.role.roleId, count(m) from ProjectMember m group by m.role.roleId")
    List<Object[]> countByRole();
    /** Chỉ đếm membership đã gán cho các user trong trang; không coi lead chưa có membership là một thành viên. */
    @org.springframework.data.jpa.repository.Query("select m.user.userId, count(m) from ProjectMember m where m.user.userId in :ids group by m.user.userId")
    List<Object[]> adminMembershipCounts(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Integer> ids);
    @org.springframework.data.jpa.repository.Query("select count(m) from ProjectMember m where m.project.projectId = :projectId and upper(m.role.roleName) = 'PROJECT_MANAGER' and m.user.userId <> :excludedUserId")
    long countOtherProjectManagers(@org.springframework.data.repository.query.Param("projectId") int projectId,
            @org.springframework.data.repository.query.Param("excludedUserId") int excludedUserId);
    List<ProjectMember> findByProject_ProjectId(int projectId);
    Optional<ProjectMember> findByProject_ProjectIdAndUser_UserId(int projectId, int userId);
    boolean existsByProject_ProjectIdAndRole_RoleName(int projectId, String roleName);
    boolean existsByRole_RoleId(int roleId);
}
