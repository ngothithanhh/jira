package com.example.jira.service.impl;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.entity.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueService;
import com.example.jira.service.NotificationService;
import com.example.jira.enums.NotificationType;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {
    private final ProjectSecurity projectSecurity;
    private final ProjectRepository projectRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueTransitionRepository issueTransitionRepository;
    private final NotificationService notificationService;
    @Override
    public IssueResponse createIssue(CreateIssueRequest request) {
        if (!projectSecurity.hasPermission(request.getProjectId(), PermissionName.CREATE_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền tạo công việc trong dự án này!");
        }

        Issue issue = new Issue();

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dự án!"));
        issue.setProject(project);

        IssueType issueType = issueTypeRepository.findById(request.getIssueTypeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại công việc!"));
        issue.setIssueType(issueType);

        issue.setSummary(request.getSummary());
        issue.setDescription(request.getDescription());
        issue.setPriority(request.getPriority());

        if (request.getParentIssueId() != null) {
            Issue parentIssue = issueRepository.findById(request.getParentIssueId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc cha!"));
            issue.setParentIssue(parentIssue);
        }

        int userId = SecurityUtils.getCurrentUserId();
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng!"));
        issue.setReporter(reporter);


        IssueStatus defaultStatus = issueStatusRepository.findById(1)
                .orElseThrow(() -> new RuntimeException("Chưa cấu hình trạng thái mặc định trong hệ thống!"));
        issue.setStatus(defaultStatus);

        int maxIssueNumber = issueRepository.findMaxIssueNumberByProjectId(project.getProjectId()).orElse(0);
        int nextIssueNumber = maxIssueNumber + 1;
        issue.setIssueNumber(nextIssueNumber);
        issue.setIssueKey(project.getProjectKey() + "-" + nextIssueNumber);

        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());

        issue.setStoryPoints(0);


        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    public IssueResponse updateIssue(int issueId, UpdateIssueRequest request) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa công việc này!");
        }

        if (request.getSummary() != null) issue.setSummary(request.getSummary());
        if (request.getDescription() != null) issue.setDescription(request.getDescription());
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        if (request.getStoryPoints() != null) issue.setStoryPoints(request.getStoryPoints());

        issue.setUpdatedAt(LocalDateTime.now());
        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    public IssueResponse assignIssue(int issueId, Integer assigneeId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền gán công việc này!");
        }

        if (assigneeId == null) {
            issue.setAssignee(null); 
        } else {
            ProjectMember member = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(issue.getProject().getProjectId(), assigneeId)
                    .orElseThrow(() -> new RuntimeException("Người dùng này không thuộc dự án, không thể gán task!"));
            issue.setAssignee(member.getUser());
            
            int actorId = SecurityUtils.getCurrentUserId();
            User actor = userRepository.findById(actorId).orElse(null);
            String actorName = actor != null ? actor.getFullName() : "Hệ thống";
            String message = actorName + " đã giao công việc " + issue.getIssueKey() + " cho bạn.";
            notificationService.sendNotification(assigneeId, issueId, actorId, NotificationType.ISSUE_ASSIGNED, message);
        }

        issue.setUpdatedAt(LocalDateTime.now());
        return IssueMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    public IssueResponse changeIssueStatus(int issueId, int statusId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền thay đổi trạng thái công việc này!");
        }

        IssueStatus toStatus = issueStatusRepository.findById(statusId)
                .orElseThrow(() -> new RuntimeException("Trạng thái không hợp lệ!"));

        if (issue.getStatus().getStatusId() == statusId) {
            return IssueMapper.toResponse(issue); 
        }

        IssueTransition transition = new IssueTransition();
        transition.setIssue(issue);
        transition.setFromStatus(issue.getStatus());
        transition.setToStatus(toStatus);
        
        int userId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(userId).orElse(null);
        transition.setUser(currentUser);
        transition.setTransitionedAt(LocalDateTime.now());
        
        issueTransitionRepository.save(transition);

        issue.setStatus(toStatus);
        issue.setUpdatedAt(LocalDateTime.now());

        Issue savedIssue = issueRepository.save(issue);

        int actorId = SecurityUtils.getCurrentUserId();
        User actor = userRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Hệ thống";
        String message = actorName + " đã cập nhật trạng thái công việc " + issue.getIssueKey() + " thành " + toStatus.getStatusName() + ".";

        if (issue.getReporter() != null && issue.getReporter().getUserId() != actorId) {
            notificationService.sendNotification(issue.getReporter().getUserId(), issueId, actorId, NotificationType.ISSUE_STATUS_CHANGED, message);
        }
        if (issue.getAssignee() != null && issue.getAssignee().getUserId() != actorId && 
           (issue.getReporter() == null || issue.getReporter().getUserId() != issue.getAssignee().getUserId())) {
            notificationService.sendNotification(issue.getAssignee().getUserId(), issueId, actorId, NotificationType.ISSUE_STATUS_CHANGED, message);
        }

        return IssueMapper.toResponse(savedIssue);
    }
}
