package com.example.jira.dto.project;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberResponse {
    private int userId;
    private String name;
    private String email;
    private int roleId;
    private String role;
    private LocalDateTime assignedAt;
}
