package com.example.jira.service;

import com.example.jira.dto.project.CreateProjectRequest;
import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.dto.project.UpdateProjectRequest;

import java.util.List;

public interface ProjectService {
    ProjectSummary createProject(CreateProjectRequest request);

    List<ProjectSummary> getProjects();

    ProjectSummary getProject(int id);

    ProjectSummary updateProject(int id, UpdateProjectRequest request);

    void deleteProject(int id);
}
