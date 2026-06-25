package com.example.jira.service;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.entity.IssueStatus;
import com.example.jira.entity.ProjectMember;

import java.util.List;

public interface IssueService {
    IssueResponse createIssue(CreateIssueRequest request);
    IssueResponse updateIssue(int issueId, UpdateIssueRequest request);
    IssueResponse assignIssue(int issueId, Integer assigneeId);
    IssueResponse changeIssueStatus(int issueId, int statusId);

}
