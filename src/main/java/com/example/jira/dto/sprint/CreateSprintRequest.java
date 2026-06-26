package com.example.jira.dto.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateSprintRequest {
    @NotNull
    private Integer projectId;

    @NotBlank
    private String sprintName;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String goal;
}
