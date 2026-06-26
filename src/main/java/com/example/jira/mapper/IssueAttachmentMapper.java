package com.example.jira.mapper;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import com.example.jira.entity.IssueAttachment;

public class IssueAttachmentMapper {
    public static IssueAttachmentResponse toResponse(IssueAttachment attachment) {
        if (attachment == null) return null;

        IssueAttachmentResponse res = new IssueAttachmentResponse();
        res.setAttachmentId(attachment.getAttachmentId());
        if (attachment.getIssue() != null) {
            res.setIssueId(attachment.getIssue().getIssueId());
        }
        res.setFilePath(attachment.getFilePath());
        
        if (attachment.getUser() != null) {
            res.setUploadedByUserId(attachment.getUser().getUserId());
            res.setUploadedByUserName(attachment.getUser().getFullName());
        }
        res.setUploadedAt(attachment.getUploadedAt());

        return res;
    }
}
