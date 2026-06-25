package com.example.jira.service;

import com.example.jira.dto.project.AddMemberRequest;
import com.example.jira.dto.project.ProjectMemberResponse;

import java.util.List;

public interface ProjectMemberService {
    void addMember(int projectId, AddMemberRequest request);
    List<ProjectMemberResponse> getMembers(int projectId);
    void removeMember(int projectId, int userId);
}
