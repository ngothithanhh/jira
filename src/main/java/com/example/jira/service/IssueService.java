package com.example.jira.service;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.IssueTransitionResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.entity.IssueStatus;
import com.example.jira.entity.ProjectMember;

import java.util.List;
import org.springframework.data.domain.Page;

public interface IssueService {
    IssueResponse createIssue(CreateIssueRequest request);
    IssueResponse updateIssue(int issueId, UpdateIssueRequest request);
    IssueResponse assignIssue(int issueId, Integer assigneeId);
    IssueResponse changeIssueStatus(int issueId, int statusId);
    List<IssueResponse> getIssuesByProject(int projectId);
    Page<IssueResponse> searchIssues(int projectId, String q, Integer statusId, Integer assigneeId,
                                     Integer issueTypeId, int page, int size, String sort);
    IssueResponse getIssue(int issueId);
    List<IssueTransitionResponse> getIssueTransitions(int issueId);
    void deleteIssue(int issueId);

}
