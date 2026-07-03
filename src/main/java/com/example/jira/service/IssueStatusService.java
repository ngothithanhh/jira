package com.example.jira.service;

import com.example.jira.dto.issue.issue_status.IssueStatusRequest;
import com.example.jira.dto.issue.issue_status.IssueStatusResponse;

import java.util.List;

public interface IssueStatusService {
    List<IssueStatusResponse> getAllStatuses();
    IssueStatusResponse createStatus(IssueStatusRequest request);
    IssueStatusResponse updateStatus(int id, IssueStatusRequest request);
    void deleteStatus(int id);
}
