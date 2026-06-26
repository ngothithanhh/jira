package com.example.jira.mapper;

import com.example.jira.dto.issue.CommentResponse;
import com.example.jira.entity.IssueComment;

public class IssueCommentMapper {
    public static CommentResponse toResponse(IssueComment comment) {
        if (comment == null) return null;
        
        CommentResponse res = new CommentResponse();
        res.setCommentId(comment.getCommentId());
        if (comment.getIssue() != null) res.setIssueId(comment.getIssue().getIssueId());
        if (comment.getUser() != null) {
            res.setUserId(comment.getUser().getUserId());
            res.setUserName(comment.getUser().getFullName());
        }
        res.setCommentText(comment.getCommentText());
        res.setCreatedAt(comment.getCreatedAt());
        return res;
    }
}
