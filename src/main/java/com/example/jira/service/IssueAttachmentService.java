package com.example.jira.service;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IssueAttachmentService {
    IssueAttachmentResponse uploadAttachment(int issueId, MultipartFile file);
    List<IssueAttachmentResponse> getAttachments(int issueId);

    @Transactional(readOnly = true)
    byte[] downloadAttachment(int attachmentId);

    void deleteAttachment(int attachmentId);
}
