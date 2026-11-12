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
        // Không suy metadata từ URL legacy; client phân biệt dữ liệu chưa có với file rỗng thật.
        res.setFileName(attachment.getFileName());
        res.setContentType(attachment.getContentType());
        res.setFileSize(attachment.getFileSize());
        res.setDownloadAvailable("LOCAL_DB".equals(attachment.getStorageType()));

        return res;
    }
}
