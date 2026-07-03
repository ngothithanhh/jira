package com.example.jira.repository;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.entity.ProjectIssueType;
import com.example.jira.entity.ProjectIssueTypeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectIssueTypeRepository extends JpaRepository<ProjectIssueType,ProjectIssueTypeId> {
    boolean existsByIssueType_IssueTypeId(int id);
    List<ProjectIssueType> findByProjectId(int projectId);
    boolean existsById(ProjectIssueTypeId id);
}
