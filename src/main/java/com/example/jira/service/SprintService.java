package com.example.jira.service;

import com.example.jira.dto.sprint.CreateSprintRequest;
import com.example.jira.dto.sprint.SprintResponse;

import java.util.List;

public interface SprintService {
    SprintResponse createSprint(CreateSprintRequest request);
    SprintResponse getSprint(int sprintId);
    List<SprintResponse> getSprintsByProject(int projectId);
    void addIssueToSprint(int sprintId, int issueId);
    void removeIssueFromSprint(int sprintId, int issueId);
    void startSprint(int sprintId);
    void completeSprint(int sprintId, Integer moveToSprintId);
}
