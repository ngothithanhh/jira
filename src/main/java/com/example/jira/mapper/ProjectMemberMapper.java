package com.example.jira.mapper;

import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.entity.ProjectMember;

public class ProjectMemberMapper {
    public static ProjectMemberResponse toResponse(ProjectMember projectMember){
        return ProjectMemberResponse.builder()
                .userId(projectMember.getUser().getUserId())
                .name(projectMember.getUser().getUserName())
                .email(projectMember.getUser().getEmail())
                .roleId(projectMember.getRole().getRoleId())
                .role(projectMember.getRole().getRoleName())
                .assignedAt(projectMember.getAssignedAt())
                .build();
    }
}
