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
import com.example.jira.repository.ProjectRepository;
import com.example.jira.service.AuditLogService;
import com.example.jira.enums.EntityType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class IssueCommentServiceImpl implements IssueCommentService {

    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectSecurity projectSecurity;
    private final NotificationService notificationService;
    private final ProjectRepository projects;
    private final jakarta.persistence.EntityManager entityManager;
    private final AuditLogService audit;

    /** Tạo comment trên issue được VIEW_PROJECT; khóa project dùng chung với xóa issue, lưu comment/audit/notification cùng transaction. */
    @Override
    @Transactional
    public CommentResponse addComment(int issueId, CreateCommentRequest request) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw error(HttpStatus.FORBIDDEN,"Bạn không có quyền bình luận trong dự án này!");
        }
        text(request==null?null:request.getCommentText());
        lockProject(issue.getProject().getProjectId()); entityManager.refresh(issue);
        projectSecurity.requirePermission(issue.getProject().getProjectId(),PermissionName.VIEW_PROJECT);

        int userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED,"Lỗi xác thực người dùng!"));

        IssueComment comment = new IssueComment();
        comment.setIssue(issue);
        comment.setUser(user);
        comment.setCommentText(request.getCommentText());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(comment.getCreatedAt());

        IssueComment savedComment = issueCommentRepository.save(comment);
        audit.logProjectAction("CREATE_COMMENT",EntityType.COMMENT,savedComment.getCommentId(),issue.getProject().getProjectId(),"Thêm bình luận cho issue ID " + issueId);

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

        // Flush trước mapper để client nhận version đã được DB ghi, lỗi cuối luồng rollback cả audit/notification.
        entityManager.flush(); return IssueCommentMapper.toResponse(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByIssue(int issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw error(HttpStatus.FORBIDDEN,"Bạn không có quyền xem bình luận trong dự án này!");
        }

        return issueCommentRepository.findByIssue_IssueIdOrderByCreatedAtAsc(issueId)
                .stream()
                .map(IssueCommentMapper::toResponse)
                .collect(Collectors.toList());
    }

    /** Sửa text đúng comment/issue sau ownership/quyền/version; không gửi lại thông báo bình luận mới. */
    @Override @Transactional
    public CommentResponse updateComment(int issueId,int commentId,com.example.jira.dto.issue.UpdateCommentRequest request) {
        if (request==null) throw error(HttpStatus.BAD_REQUEST,"Thiếu nội dung");
        text(request.commentText());
        IssueComment comment = writable(issueId,commentId,request.expectedVersion());
        comment.setCommentText(request.commentText()); comment.setUpdatedAt(LocalDateTime.now()); issueCommentRepository.save(comment);
        audit.logProjectAction("UPDATE_COMMENT",EntityType.COMMENT,commentId,comment.getIssue().getProject().getProjectId(),"Sửa bình luận của issue ID " + issueId);
        entityManager.flush(); return IssueCommentMapper.toResponse(comment);
    }

    /** Xóa comment cùng version client đã tải; audit chung transaction, không xóa issue hoặc notification lịch sử. */
    @Override @Transactional
    public void deleteComment(int issueId,int commentId,Long expectedVersion) {
        IssueComment comment = writable(issueId,commentId,expectedVersion);
        issueCommentRepository.delete(comment); entityManager.flush();
        audit.logProjectAction("DELETE_COMMENT",EntityType.COMMENT,commentId,comment.getIssue().getProject().getProjectId(),"Xóa bình luận của issue ID " + issueId);
    }

    /** Issue quyết định project; chỉ tác giả còn VIEW_PROJECT hoặc người có EDIT_ISSUE được sửa, cùng khóa project rồi refresh/so version. */
    private IssueComment writable(int issueId,int commentId,Long version) {
        if (version==null || version<0) throw error(HttpStatus.BAD_REQUEST,"Cần expectedVersion hợp lệ");
        Issue issue = issueRepository.findById(issueId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy công việc"));
        int projectId=issue.getProject().getProjectId(); projectSecurity.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        IssueComment comment = issueCommentRepository.findById(commentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy bình luận"));
        if (comment.getIssue()==null || comment.getIssue().getIssueId()!=issueId) throw error(HttpStatus.NOT_FOUND,"Bình luận không thuộc issue này");
        int actorId=SecurityUtils.getCurrentUserId();
        if ((comment.getUser()==null || comment.getUser().getUserId()!=actorId) && !projectSecurity.hasPermission(projectId,PermissionName.EDIT_ISSUE)) throw error(HttpStatus.FORBIDDEN,"Không có quyền sửa/xóa bình luận");
        lockProject(projectId); entityManager.refresh(comment);
        // Quyền có thể vừa bị thu hồi khi chờ khóa; kiểm lại ownership và role đã refresh trước ghi.
        projectSecurity.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        if ((comment.getUser()==null || comment.getUser().getUserId()!=actorId) && !projectSecurity.hasPermission(projectId,PermissionName.EDIT_ISSUE)) throw error(HttpStatus.FORBIDDEN,"Không có quyền sửa/xóa bình luận");
        if (!java.util.Objects.equals(comment.getVersion(),version)) throw error(HttpStatus.CONFLICT,"Bình luận đã thay đổi, vui lòng tải lại");
        return comment;
    }
    /** projectId lấy từ issue đã kiểm quyền; khóa tới cuối transaction, không tự ghi dữ liệu project. */
    private void lockProject(int projectId) {
        var project=projects.findByIdForUpdate(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy dự án"));
        entityManager.refresh(project);
        // EntityManager có thể đã nạp role cũ. Refresh đúng cặp project/user của actor trước kiểm quyền lần hai.
        var membership=entityManager.find(com.example.jira.entity.ProjectMember.class,new com.example.jira.entity.ProjectMemberId(projectId,SecurityUtils.getCurrentUserId()));
        if (membership!=null) try { entityManager.refresh(membership); }
        catch (jakarta.persistence.EntityNotFoundException removed) { throw error(HttpStatus.FORBIDDEN,"Bạn không còn là thành viên dự án"); }
    }
    /** Service cũng chặn text trống/quá dài cho caller nội bộ không qua Bean Validation. */
    private void text(String value) { if (value==null || value.isBlank() || value.length()>10000) throw error(HttpStatus.BAD_REQUEST,"Bình luận cần 1..10000 ký tự"); }
    /** Tạo lỗi có HTTP status, không ghi nội dung comment vào thông báo lỗi. */
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
