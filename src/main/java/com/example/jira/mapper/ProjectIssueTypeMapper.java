package com.example.jira.mapper;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.entity.ProjectIssueType;

public class ProjectIssueTypeMapper {
    public static ProjectIssueTypeResponse toResponse(ProjectIssueType projectIssueType){
        return ProjectIssueTypeResponse.builder()
                .projectId(projectIssueType.getProject().getProjectId())
                .issueTypeId(projectIssueType.getIssueType().getIssueTypeId())
                .issueTypeName(projectIssueType.getIssueType().getTypeName())
                .isDefault(projectIssueType.isDefault())
                .build();
    }
}
