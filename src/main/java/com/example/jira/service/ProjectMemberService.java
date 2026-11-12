package com.example.jira.service;

import com.example.jira.dto.project.MemberCandidate;
import com.example.jira.dto.project.ProjectMemberResponse;

import java.util.List;

public interface ProjectMemberService {
    void updateRole(int projectId,int userId,int roleId);
    List<ProjectMemberResponse> getMembers(int projectId);
    List<MemberCandidate> findCandidate(int projectId, String email);
    void removeMember(int projectId, int userId);
}
