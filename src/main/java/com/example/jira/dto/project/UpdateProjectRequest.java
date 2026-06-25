package com.example.jira.dto.project;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProjectRequest {
    private String projectName;
    private String description;
}
