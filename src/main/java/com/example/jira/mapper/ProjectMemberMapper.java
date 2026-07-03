package com.example.jira.mapper;

import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.entity.ProjectMember;

public class ProjectMemberMapper {
    public static ProjectMemberResponse toResponse(ProjectMember projectMember){
        return ProjectMemberResponse.builder()
                .userId(projectMember.getUser().getUserId())
                .userName(projectMember.getUser().getUserName())
                .fullName(projectMember.getUser().getFullName())
                .email(projectMember.getUser().getEmail())
                .role(projectMember.getRole().getRoleName())
                .roleId(projectMember.getRole().getRoleId())
                .assignedAt(projectMember.getAssignedAt())
                .build();
    }
}
