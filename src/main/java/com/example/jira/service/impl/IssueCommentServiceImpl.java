package com.example.jira.service.impl;

import com.example.jira.dto.issue.CommentResponse;
import com.example.jira.dto.issue.CreateCommentRequest;
import com.example.jira.entity.Issue;
import com.example.jira.entity.IssueComment;
import com.example.jira.entity.User;
import com.example.jira.mapper.IssueCommentMapper;
import com.example.jira.repository.IssueCommentRepository;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueCommentService;
import com.example.jira.service.NotificationService;
import com.example.jira.enums.NotificationType;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueCommentServiceImpl implements IssueCommentService {

    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectSecurity projectSecurity;
    private final NotificationService notificationService;
    private final com.example.jira.service.IssueCollaborationAccess access;
    private final com.example.jira.service.AuditLogService audit;

    @Override
    @Transactional
    public CommentResponse addComment(int issueId, CreateCommentRequest request) {
        Issue issue = issueRepository.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền bình luận trong dự án này!");
        }

        int userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng!"));

        IssueComment comment = new IssueComment();
        comment.setIssue(issue);
        comment.setUser(user);
        comment.setCommentText(request.getCommentText());
        comment.setCreatedAt(LocalDateTime.now());

        IssueComment savedComment = issueCommentRepository.save(comment);

        audit.logAction("COMMENT_CREATED", com.example.jira.enums.EntityType.ISSUE, issueId, savedComment.getCommentText());

        // Notify reporter and assignee
        String actorName = user.getFullName() != null ? user.getFullName() : "Ai đó";
        String message = actorName + " đã bình luận vào công việc " + issue.getIssueKey() + ".";

        if (issue.getReporter() != null && issue.getReporter().getUserId() != userId) {
            notificationService.sendNotification(issue.getReporter().getUserId(), issueId, userId, NotificationType.ISSUE_COMMENTED, message);
        }
        if (issue.getAssignee() != null && issue.getAssignee().getUserId() != userId &&
           (issue.getReporter() == null || issue.getReporter().getUserId() != issue.getAssignee().getUserId())) {
            notificationService.sendNotification(issue.getAssignee().getUserId(), issueId, userId, NotificationType.ISSUE_COMMENTED, message);
        }

        return IssueCommentMapper.toResponse(savedComment);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(int issueId, int commentId, CreateCommentRequest request) {
        Issue issue = access.requireView(issueId);
        IssueComment comment = findComment(issueId, commentId);
        access.requireOwnerOrEditor(issue, comment.getUser().getUserId());
        comment.setCommentText(request.getCommentText());
        audit.logAction("COMMENT_UPDATED", com.example.jira.enums.EntityType.ISSUE, issueId, request.getCommentText());
        return IssueCommentMapper.toResponse(issueCommentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(int issueId, int commentId) {
        Issue issue = access.requireView(issueId);
        IssueComment comment = findComment(issueId, commentId);
        access.requireOwnerOrEditor(issue, comment.getUser().getUserId());
        audit.logAction("COMMENT_DELETED", com.example.jira.enums.EntityType.ISSUE, issueId, comment.getCommentText());
        issueCommentRepository.delete(comment);
    }

    private IssueComment findComment(int issueId, int id) {
        return issueCommentRepository.findById(id).filter(c -> c.getIssue().getIssueId() == issueId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy bình luận"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByIssue(int issueId) {
        Issue issue = issueRepository.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem bình luận trong dự án này!");
        }

        return issueCommentRepository.findByIssue_IssueIdOrderByCreatedAtAsc(issueId)
                .stream()
                .map(IssueCommentMapper::toResponse)
                .collect(Collectors.toList());
    }
}
