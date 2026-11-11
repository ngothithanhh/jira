package com.example.jira.service;

import com.example.jira.dto.issue.CommentResponse;
import com.example.jira.dto.issue.CreateCommentRequest;

import java.util.List;

public interface IssueCommentService {
    CommentResponse updateComment(int issueId,int commentId,com.example.jira.dto.issue.UpdateCommentRequest request);
    void deleteComment(int issueId,int commentId,Long expectedVersion);
    CommentResponse addComment(int issueId, CreateCommentRequest request);
    List<CommentResponse> getCommentsByIssue(int issueId);
}
