package com.example.jira.dto.project;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class UpdateProjectRequest {
    @NotBlank @Size(max = 100) private String projectName;
    @Size(max = 255) private String description;
}
