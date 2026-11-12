package com.example.jira.dto.project;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class CreateProjectRequest {
    @NotBlank @Size(max = 100) private String projectName;
    @NotBlank @Size(max = 10) private String projectKey;
    @Size(max = 255) private String description;
}
