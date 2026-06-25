package com.example.jira.repository;

import com.example.jira.entity.ProjectMember;
import com.example.jira.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
    List<ProjectMember> findByProject_ProjectId(int projectId);
    Optional<ProjectMember> findByProject_ProjectIdAndUser_UserId(int projectId, int userId);
}
