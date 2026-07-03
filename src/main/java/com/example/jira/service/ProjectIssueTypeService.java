package com.example.jira.service;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;

import java.util.List;

public interface ProjectIssueTypeService {
    List<ProjectIssueTypeResponse> getProjectIssueTypes(int projectId);
    void assignIssueType(int projectId, int issueTypeId, boolean isDefault);
    void setDefaultIssueType(int projectId, int issueTypeId);
    void removeIssueType(int projectId, int issueTypeId);
}
