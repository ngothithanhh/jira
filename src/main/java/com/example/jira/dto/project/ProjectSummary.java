package com.example.jira.dto.project;

import com.example.jira.dto.user.UserSummary;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectSummary {
    private int projectId;
    private String projectName;
    private String projectKey;
    private String description;
    private UserSummary leader;

}
