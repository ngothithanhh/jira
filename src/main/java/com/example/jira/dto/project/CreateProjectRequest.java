package com.example.jira.dto.project;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectRequest {
    private String projectName;
    private String projectKey;
    private String description;
}
