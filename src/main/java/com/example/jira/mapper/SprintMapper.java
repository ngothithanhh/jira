package com.example.jira.mapper;

import com.example.jira.dto.sprint.SprintResponse;
import com.example.jira.entity.Sprint;

public class SprintMapper {
    public static SprintResponse toResponse(Sprint sprint) {
        if (sprint == null) return null;
        
        SprintResponse dto = new SprintResponse();
        dto.setSprintId(sprint.getSprintId());
        if (sprint.getProject() != null) {
            dto.setProjectId(sprint.getProject().getProjectId());
        }
        dto.setSprintName(sprint.getSprintName());
        dto.setStartDate(sprint.getStartDate());
        dto.setEndDate(sprint.getEndDate());
        dto.setGoal(sprint.getGoal());
        dto.setCreatedAt(sprint.getCreatedAt());
        dto.setState(sprint.getState());
        return dto;
    }
}
