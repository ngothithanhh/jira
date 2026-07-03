package com.example.jira.service;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.dto.issue.issue_type.IssueTypeRequest;
import com.example.jira.dto.issue.issue_type.IssueTypeResponse;

import java.util.List;

public interface IssueTypeService {
    List<IssueTypeResponse> getAllIssueTypes();
    IssueTypeResponse createIssueType(IssueTypeRequest request);
    IssueTypeResponse updateIssueType(int id, IssueTypeRequest request);
    void deleteIssueType(int id);
}
