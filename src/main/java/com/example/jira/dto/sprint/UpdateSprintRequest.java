package com.example.jira.dto.sprint;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class UpdateSprintRequest {
    private String sprintName;
    private String goal;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
