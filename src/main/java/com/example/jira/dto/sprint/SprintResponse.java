package com.example.jira.dto.sprint;

import com.example.jira.enums.SprintState;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SprintResponse {
    private int sprintId;
    private int projectId;
    private String sprintName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String goal;
    private LocalDateTime createdAt;
    private SprintState state;
}
