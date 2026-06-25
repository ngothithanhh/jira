package com.example.jira.mapper;

import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {
    public ProjectSummary toResponse(Project project){
        return ProjectSummary.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .projectKey(project.getProjectKey())
                .description(project.getDescription())
                .leader(UserMapper.toResponse(project.getLead()))
                .build();
    }
}
