package com.example.jira.service.impl;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.dto.issue.IssueTransitionResponse;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.entity.*;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueService;
import com.example.jira.service.AuditLogService;
import com.example.jira.service.NotificationService;
import com.example.jira.enums.NotificationType;
import com.example.jira.enums.PermissionName;
import com.example.jira.enums.EntityType;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import com.example.jira.enums.SprintState;

@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {
    private final BoardIssueRepository boardIssueRepository;
    private final ProjectSecurity projectSecurity;
    private final ProjectRepository projectRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueTransitionRepository issueTransitionRepository;
    private final NotificationService notificationService;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "dueDate", "priority", "backlogRank", "boardPosition", "issueKey");

    @Override
    @Transactional
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
        if (!projectIssueTypeRepository.existsByProject_ProjectIdAndIssueType_IssueTypeId(
                project.getProjectId(), issueType.getIssueTypeId())) {
            throw new RuntimeException("Loại công việc không thuộc dự án này!");
        }
        issue.setIssueType(issueType);

        issue.setSummary(request.getSummary());
        issue.setDescription(request.getDescription());
        issue.setPriority(request.getPriority());
        issue.setDueDate(request.getDueDate());
        issue.setBacklogRank(request.getBacklogRank() != null
                ? request.getBacklogRank()
                : issueRepository.findMaxBacklogRankByProjectId(project.getProjectId()) + 1024);
        issue.setBoardPosition(request.getBoardPosition());

        if (request.getParentIssueId() != null) {
            Issue parentIssue = issueRepository.findByIssueIdAndDeletedAtIsNull(request.getParentIssueId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc cha!"));
            if (parentIssue.getProject().getProjectId() != project.getProjectId()) {
                throw new RuntimeException("Công việc cha phải thuộc cùng dự án!");
            }
            issue.setParentIssue(parentIssue);
        }

        int userId = SecurityUtils.getCurrentUserId();
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng!"));
        issue.setReporter(reporter);


        issue.setStatus(project.getDefaultIssueStatus());

        int maxIssueNumber = issueRepository.findMaxIssueNumberByProjectId(project.getProjectId()).orElse(0);
        int nextIssueNumber = maxIssueNumber + 1;
        issue.setIssueNumber(nextIssueNumber);
        issue.setIssueKey(project.getProjectKey() + "-" + nextIssueNumber);

        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());

        issue.setStoryPoints(0);


        Issue savedIssue = issueRepository.save(issue);
        auditLogService.logAction("ISSUE_CREATED", EntityType.ISSUE, savedIssue.getIssueId(),
                "Tạo issue " + savedIssue.getIssueKey() + " trong project " + project.getProjectKey());
        return IssueMapper.toResponse(savedIssue);
    }

    @Override
    @Transactional
    public IssueResponse updateIssue(int issueId, UpdateIssueRequest request) {
        Issue issue = getActiveIssueOrThrow(issueId);

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa công việc này!");
        }

        if (request.getSummary() != null) issue.setSummary(request.getSummary());
        if (request.getDescription() != null) issue.setDescription(request.getDescription());
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        if (request.getStoryPoints() != null) issue.setStoryPoints(request.getStoryPoints());
        if (request.getDueDate() != null) issue.setDueDate(request.getDueDate());
        if (request.getBacklogRank() != null) issue.setBacklogRank(request.getBacklogRank());
        if (request.getBoardPosition() != null) issue.setBoardPosition(request.getBoardPosition());

        issue.setUpdatedAt(LocalDateTime.now());
        Issue savedIssue = issueRepository.save(issue);
        auditLogService.logAction("ISSUE_UPDATED", EntityType.ISSUE, issueId,
                "Cập nhật issue " + savedIssue.getIssueKey());
        return IssueMapper.toResponse(savedIssue);
    }

    @Override
    @Transactional
    public IssueResponse assignIssue(int issueId, Integer assigneeId) {
        Issue issue = getActiveIssueOrThrow(issueId);

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
        Issue savedIssue = issueRepository.save(issue);
        auditLogService.logAction("ISSUE_ASSIGNED", EntityType.ISSUE, issueId,
                "Assignee=" + assigneeId + "; issue " + savedIssue.getIssueKey());
        return IssueMapper.toResponse(savedIssue);
    }

    @Override
    @Transactional
    public IssueResponse changeIssueStatus(int issueId, int statusId) {
        Issue issue = issueRepository.findLockedActiveById(issueId).orElseThrow();

        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền thay đổi trạng thái công việc này!");
        }

        IssueStatus toStatus = issueStatusRepository.findById(statusId)
                .orElseThrow(() -> new RuntimeException("Trạng thái không hợp lệ!"));

        if (issue.getStatus().getStatusId() == statusId) {
            return IssueMapper.toResponse(issue); 
        }

        if (boardIssueRepository.existsByIssue_IssueId(issueId)) throw new RuntimeException("Vui lòng chuyển trạng thái công việc trực tiếp trên bảng");
        if (!workflowRuleRepository.existsByProject_ProjectIdAndFromStatus_StatusIdAndToStatus_StatusId(
                issue.getProject().getProjectId(), issue.getStatus().getStatusId(), statusId)) {
            throw new RuntimeException("Chuyển đổi trạng thái không hợp lệ theo quy trình!");
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
        auditLogService.logAction("ISSUE_STATUS_CHANGED", EntityType.ISSUE, issueId,
                "Issue " + issue.getIssueKey() + " đổi trạng thái từ " + transition.getFromStatus().getStatusName()
                        + " sang " + toStatus.getStatusName());

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

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getIssuesByProject(int projectId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem dự án này!");
        }
        List<Issue> issues = issueRepository.findByProject_ProjectIdAndDeletedAtIsNull(projectId);
        return issues.stream().map(IssueMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IssueResponse> searchIssues(int projectId, String q, Integer statusId, Integer assigneeId,
                                            Integer issueTypeId, int page, int size, String sort) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem dự án này!");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new RuntimeException("Số trang phải từ 0 trở lên và số bản ghi mỗi trang phải từ 1 đến 100!");
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        String query = q == null || q.isBlank() ? null : q.trim();
        return issueRepository.searchActiveIssues(projectId, query, statusId, assigneeId, issueTypeId, pageable)
                .map(IssueMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse getIssue(int issueId) {
        Issue issue = getActiveIssueOrThrow(issueId);
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem công việc này!");
        }
        return IssueMapper.toResponse(issue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueTransitionResponse> getIssueTransitions(int issueId) {
        Issue issue = getActiveIssueOrThrow(issueId);
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem lịch sử công việc này!");
        }
        return issueTransitionRepository.findByIssue_IssueIdOrderByTransitionedAtDesc(issueId).stream()
                .map(transition -> IssueTransitionResponse.builder()
                        .transitionId(transition.getTransitionId())
                        .fromStatusId(transition.getFromStatus().getStatusId())
                        .fromStatusName(transition.getFromStatus().getStatusName())
                        .toStatusId(transition.getToStatus().getStatusId())
                        .toStatusName(transition.getToStatus().getStatusName())
                        .userId(transition.getUser() == null ? null : transition.getUser().getUserId())
                        .userName(transition.getUser() == null ? null : transition.getUser().getUserName())
                        .transitionedAt(transition.getTransitionedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getProductBacklog(int projectId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem danh sách chờ của dự án này!");
        }
        return issueRepository.findProductBacklog(projectId, List.of(SprintState.FUTURE, SprintState.ACTIVE))
                .stream().map(IssueMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void rankBacklogIssue(int projectId, int issueId, Integer beforeIssueId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Bạn không có quyền sắp xếp danh sách chờ của dự án này!");
        }
        Issue issue = getActiveIssueOrThrow(issueId);
        if (issue.getProject().getProjectId() != projectId) {
            throw new RuntimeException("Công việc phải thuộc cùng dự án!");
        }
        List<Issue> backlog = issueRepository.findProductBacklog(projectId, List.of(SprintState.FUTURE, SprintState.ACTIVE))
                .stream().sorted(Comparator.comparing(Issue::getBacklogRank,
                        Comparator.nullsLast(Long::compareTo)).thenComparing(Issue::getIssueId)).toList();
        if (backlog.stream().noneMatch(i -> i.getIssueId() == issueId)) {
            throw new RuntimeException("Chỉ có thể sắp xếp công việc trong danh sách chờ của sản phẩm!");
        }
        if (beforeIssueId != null && beforeIssueId == issueId) {
            return;
        }
        java.util.ArrayList<Issue> reordered = new java.util.ArrayList<>(backlog);
        reordered.removeIf(i -> i.getIssueId() == issueId);
        if (beforeIssueId == null) reordered.add(issue);
        else {
            int index = -1;
            for (int i = 0; i < reordered.size(); i++) if (reordered.get(i).getIssueId() == beforeIssueId) { index = i; break; }
            if (index < 0) throw new RuntimeException("Công việc đích phải thuộc danh sách chờ của cùng dự án!");
            reordered.add(index, issue);
        }
        for (int i = 0; i < reordered.size(); i++) reordered.get(i).setBacklogRank((long) (i + 1) * 1024);
        issueRepository.saveAll(reordered);
        auditLogService.logAction("BACKLOG_RANK_CHANGED", EntityType.ISSUE, issueId,
                "Sắp xếp lại backlog issue " + issue.getIssueKey() + " trong project " + issue.getProject().getProjectKey());
    }

    @Override
    @Transactional
    public void deleteIssue(int issueId) {
        Issue issue = getActiveIssueOrThrow(issueId);
        if (!projectSecurity.hasPermission(issue.getProject().getProjectId(), PermissionName.DELETE_ISSUE)) {
            throw new RuntimeException("Bạn không có quyền xóa công việc này!");
        }
        int currentUserId = SecurityUtils.getCurrentUserId();
        User actor = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Lỗi xác thực người dùng!"));
        issue.setDeletedAt(LocalDateTime.now());
        issue.setDeletedBy(actor);
        issue.setUpdatedAt(LocalDateTime.now());
        issueRepository.save(issue);
        auditLogService.logAction("ISSUE_DELETED", EntityType.ISSUE, issueId,
                "Xóa mềm issue " + issue.getIssueKey() + " trong project " + issue.getProject().getProjectKey());
    }

    private Issue getActiveIssueOrThrow(int issueId) {
        return issueRepository.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc!"));
    }

    private Sort parseSort(String requestedSort) {
        if (requestedSort == null || requestedSort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        String[] parts = requestedSort.split(",", -1);
        String property = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(property) || parts.length > 2) {
            throw new RuntimeException("Trường sắp xếp không hợp lệ!");
        }
        Sort.Direction direction = parts.length == 2 ? Sort.Direction.fromOptionalString(parts[1].trim())
                .orElseThrow(() -> new RuntimeException("Chiều sắp xếp không hợp lệ!")) : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
