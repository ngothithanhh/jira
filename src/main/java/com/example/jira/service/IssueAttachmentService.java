package com.example.jira.service;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IssueAttachmentService {
    record Download(String fileName,String contentType,byte[] content) { }
    Download download(int issueId,int attachmentId);
    void deleteAttachmentForIssue(int issueId, int attachmentId);
    IssueAttachmentResponse uploadAttachment(int issueId, MultipartFile file);
    List<IssueAttachmentResponse> getAttachments(int issueId);
    void deleteAttachment(int attachmentId);
}
