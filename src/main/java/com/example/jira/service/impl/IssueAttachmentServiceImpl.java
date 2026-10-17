package com.example.jira.service.impl;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import com.example.jira.entity.Issue;
import com.example.jira.entity.IssueAttachment;
import com.example.jira.entity.User;
import com.example.jira.mapper.IssueAttachmentMapper;
import com.example.jira.repository.IssueAttachmentRepository;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.CloudinaryService;
import com.example.jira.service.IssueAttachmentService;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueAttachmentServiceImpl implements IssueAttachmentService {

    private final IssueAttachmentRepository attachmentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final ProjectSecurity projectSecurity;
    private final com.example.jira.service.IssueCollaborationAccess access;
    private final com.example.jira.service.AuditLogService audit;

    @Override
    @Transactional
    public IssueAttachmentResponse uploadAttachment(int issueId, MultipartFile file) {
        Issue issue = access.requireView(issueId);

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền đính kèm file trong dự án này!");
        }

        AttachmentFilePolicy.validate(file);
        try {
            String fileUrl = cloudinaryService.uploadFile(file);

            int userId = SecurityUtils.getCurrentUserId();
            User user = userRepository.findById(userId).orElseThrow();

            IssueAttachment attachment = new IssueAttachment();
            attachment.setIssue(issue);
            attachment.setFilePath(fileUrl);
            attachment.setUser(user);
            attachment.setUploadedAt(LocalDateTime.now());

            IssueAttachment saved = attachmentRepository.save(attachment);
            audit.logAction("ATTACHMENT_UPLOADED", com.example.jira.enums.EntityType.ISSUE, issueId, file.getOriginalFilename());
            return IssueAttachmentMapper.toResponse(saved);

        } catch (Exception e) {
            throw new RuntimeException("Lỗi upload file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueAttachmentResponse> getAttachments(int issueId) {
        Issue issue = issueRepository.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem trong dự án này!");
        }

        return attachmentRepository.findByIssue_IssueIdOrderByUploadedAtAsc(issueId)
                .stream()
                .map(IssueAttachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] downloadAttachment(int attachmentId) {
        IssueAttachment attachment = attachmentRepository.findById(attachmentId).orElseThrow();
        access.requireView(attachment.getIssue().getIssueId());
        try { return cloudinaryService.downloadFile(attachment.getFilePath()); }
        catch (java.io.IOException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Attachment download failed", ex);
        }
    }

    @Override
    @Transactional
    public void deleteAttachment(int attachmentId) {
        IssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file đính kèm!"));

        Issue issue = attachment.getIssue();
        if (issue.getDeletedAt() != null) {
            throw new RuntimeException("Không tìm thấy công việc!");
        }
        access.requireView(issue.getIssueId());
        int userId = SecurityUtils.getCurrentUserId();

        // Chỉ người tải lên hoặc có quyền EDIT_ISSUE mới được xóa
        if (attachment.getUser().getUserId() != userId && !projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
             throw new RuntimeException("Bạn không có quyền xóa file này!");
        }

        try {
            cloudinaryService.deleteFile(attachment.getFilePath());
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Attachment storage deletion failed", e);
        }

        audit.logAction("ATTACHMENT_DELETED", com.example.jira.enums.EntityType.ISSUE, issue.getIssueId(), "Attachment " + attachmentId);
        attachmentRepository.delete(attachment);
    }
}
