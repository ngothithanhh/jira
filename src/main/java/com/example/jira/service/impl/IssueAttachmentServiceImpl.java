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

    @Override
    @Transactional
    public IssueAttachmentResponse uploadAttachment(int issueId, MultipartFile file) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), "EDIT_ISSUE")) {
            throw new RuntimeException("Bạn không có quyền đính kèm file trong dự án này!");
        }

        try {
            String fileUrl = cloudinaryService.uploadFile(file);

            int userId = SecurityUtils.getCurrentUserId();
            User user = userRepository.findById(userId).orElseThrow();

            IssueAttachment attachment = new IssueAttachment();
            attachment.setIssue(issue);
            attachment.setFilePath(fileUrl);
            attachment.setUser(user);
            attachment.setUploadedAt(LocalDateTime.now());

            return IssueAttachmentMapper.toResponse(attachmentRepository.save(attachment));

        } catch (Exception e) {
            throw new RuntimeException("Lỗi upload file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueAttachmentResponse> getAttachments(int issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), "VIEW_PROJECT")) {
            throw new RuntimeException("Bạn không có quyền xem trong dự án này!");
        }

        return attachmentRepository.findByIssue_IssueIdOrderByUploadedAtAsc(issueId)
                .stream()
                .map(IssueAttachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAttachment(int attachmentId) {
        IssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy file đính kèm!"));

        Issue issue = attachment.getIssue();
        int userId = SecurityUtils.getCurrentUserId();

        // Chỉ người tải lên hoặc có quyền EDIT_ISSUE mới được xóa
        if (attachment.getUser().getUserId() != userId && !projectSecurity.hasPermission(issue.getProject().getProjectId(), "EDIT_ISSUE")) {
             throw new RuntimeException("Bạn không có quyền xóa file này!");
        }

        try {
            cloudinaryService.deleteFile(attachment.getFilePath());
        } catch (Exception e) {
            // Log error but continue to delete from DB
            System.out.println("Cảnh báo: Lỗi xóa file trên Cloudinary: " + e.getMessage());
        }

        attachmentRepository.delete(attachment);
    }
}
