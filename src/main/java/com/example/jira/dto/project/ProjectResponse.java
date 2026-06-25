package com.example.jira.dto.project;


import com.example.jira.dto.user.UserSummary;
import com.example.jira.entity.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {

    private int projectId;

    private String projectName;

    private String projectKey;

    private String description;

    private UserSummary leader;

    private LocalDateTime createdAt;

//    private Set<ProjectMember> projectMembers;
//
//    private Set<Board> boards;
//
//    private Set<Sprint> sprints;
//
//    private Set<ProjectVersion> projectVersions;
}


