package com.example.jira.service.impl;

import com.example.jira.dto.sprint.CreateSprintRequest;
import com.example.jira.dto.sprint.SprintResponse;
import com.example.jira.dto.sprint.UpdateSprintRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.SprintState;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.SprintMapper;
import com.example.jira.mapper.IssueMapper;
import com.example.jira.service.AuditLogService;
import com.example.jira.enums.EntityType;
import com.example.jira.enums.StatusCategory;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final SprintIssueRepository sprintIssueRepository;
    private final ProjectSecurity projectSecurity;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public SprintResponse createSprint(CreateSprintRequest request) {
        Project project = projectRepository.findById(request.getProjectId()).orElseThrow(() -> new RuntimeException("Không tìm thấy dự án"));
        if (!projectSecurity.hasPermission(project.getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }

        Sprint sprint = new Sprint();
        sprint.setProject(project);
        sprint.setSprintName(request.getSprintName());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setGoal(request.getGoal());
        validateDates(request.getStartDate(), request.getEndDate());
        sprint.setCreatedAt(LocalDateTime.now());
        sprint.setState(SprintState.FUTURE);

        sprint = sprintRepository.save(sprint);
        auditLogService.logAction("SPRINT_CREATED", EntityType.SPRINT, sprint.getSprintId(),
                "Tạo sprint " + sprint.getSprintName() + " trong project " + project.getProjectKey());
        return SprintMapper.toResponse(sprint);
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(int sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem đợt làm việc này");
        }
        return SprintMapper.toResponse(sprint);
    }

    @Override
    @Transactional
    public SprintResponse updateSprint(int sprintId, UpdateSprintRequest request) {
        Sprint sprint = getManageableSprint(sprintId);
        requireFuture(sprint, "Chỉ có thể cập nhật sprint chưa bắt đầu");
        validateDates(request.getStartDate(), request.getEndDate());
        sprint.setSprintName(request.getSprintName());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setGoal(request.getGoal());
        Sprint saved = sprintRepository.save(sprint);
        auditLogService.logAction("SPRINT_UPDATED", EntityType.SPRINT, sprintId,
                "Cập nhật sprint " + saved.getSprintName());
        return SprintMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSprint(int sprintId) {
        Sprint sprint = getManageableSprint(sprintId);
        requireFuture(sprint, "Chỉ có thể xóa sprint chưa bắt đầu");
        String name = sprint.getSprintName();
        sprintRepository.delete(sprint);
        auditLogService.logAction("SPRINT_DELETED", EntityType.SPRINT, sprintId,
                "Xóa sprint " + name + "; các issue được trả về product backlog");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> getSprintsByProject(int projectId) {
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem đợt làm việc của dự án này");
        }
        return sprintRepository.findByProject_ProjectId(projectId).stream()
                .map(SprintMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> getSprintBacklog(int sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem đợt làm việc này");
        }
        return sprintIssueRepository.findBySprint_SprintIdOrderByIssue_BacklogRankAscIssue_IssueIdAsc(sprintId).stream()
                .map(SprintIssue::getIssue).map(IssueMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void addIssueToSprint(int sprintId, int issueId) {
        Sprint sprint = getManageableSprint(sprintId);
        requireFuture(sprint, "Chỉ có thể thêm issue vào sprint chưa bắt đầu");
        Issue issue = issueRepository.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy issue"));
        if (issue.getProject().getProjectId() != sprint.getProject().getProjectId()) {
            throw new RuntimeException("Công việc phải thuộc cùng dự án");
        }
        if (sprintIssueRepository.existsOpenSprintMembership(issueId, List.of(SprintState.FUTURE, SprintState.ACTIVE))) {
            throw new RuntimeException("Công việc đã thuộc một đợt làm việc chưa đóng");
        }

        SprintIssue sprintIssue = new SprintIssue();
        sprintIssue.setId(new SprintIssueId(sprintId, issueId));
        sprintIssue.setSprint(sprint);
        sprintIssue.setIssue(issue);
        sprintIssue.setAddedAt(LocalDateTime.now());

        sprintIssueRepository.save(sprintIssue);
        auditLogService.logAction("ISSUE_ADDED_TO_SPRINT", EntityType.ISSUE, issueId,
                "Thêm issue " + issue.getIssueKey() + " vào sprint " + sprint.getSprintName());
    }

    @Override
    @Transactional
    public void removeIssueFromSprint(int sprintId, int issueId) {
        Sprint sprint = getManageableSprint(sprintId);
        requireFuture(sprint, "Chỉ có thể gỡ issue khỏi sprint chưa bắt đầu");
        if (!sprintIssueRepository.existsBySprint_SprintIdAndIssue_IssueId(sprintId, issueId)) {
            throw new RuntimeException("Công việc không thuộc đợt làm việc này");
        }
        sprintIssueRepository.deleteBySprint_SprintIdAndIssue_IssueId(sprintId, issueId);
        auditLogService.logAction("ISSUE_REMOVED_FROM_SPRINT", EntityType.ISSUE, issueId,
                "Gỡ issue khỏi sprint " + sprint.getSprintName());
    }

    @Override
    @Transactional
    public void startSprint(int sprintId) {
        Sprint sprint = getManageableSprint(sprintId);
        requireFuture(sprint, "Chỉ có thể bắt đầu sprint chưa bắt đầu");
        if (sprintRepository.existsByProject_ProjectIdAndState(sprint.getProject().getProjectId(), SprintState.ACTIVE)) {
            throw new RuntimeException("Dự án đã có đợt làm việc đang hoạt động");
        }
        sprint.setState(SprintState.ACTIVE);
        sprintRepository.save(sprint);
        auditLogService.logAction("SPRINT_STARTED", EntityType.SPRINT, sprintId,
                "Bắt đầu sprint " + sprint.getSprintName());
    }

    @Override
    @Transactional
    public void completeSprint(int sprintId, Integer moveToSprintId) {
        Sprint sprint = getManageableSprint(sprintId);
        if (sprint.getState() != SprintState.ACTIVE) {
            throw new RuntimeException("Chỉ có thể hoàn tất đợt làm việc đang hoạt động");
        }

        Sprint nextSprint = null;
        if (moveToSprintId != null) {
            nextSprint = sprintRepository.findById(moveToSprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint đích"));
            if (nextSprint.getProject().getProjectId() != sprint.getProject().getProjectId()) {
                throw new RuntimeException("Đợt làm việc đích phải thuộc cùng dự án");
            }
            if (nextSprint.getState() != SprintState.FUTURE) {
                throw new RuntimeException("Đợt làm việc đích phải ở trạng thái chưa bắt đầu");
            }
        }

        List<SprintIssue> sprintIssues = sprintIssueRepository.findBySprint_SprintId(sprintId);
        for (SprintIssue si : sprintIssues) {
            Issue issue = si.getIssue();
            boolean isDone = issue.getStatus() != null && issue.getStatus().getStatusCategory() == StatusCategory.DONE;
            si.setCompleted(isDone);
            si.setFinalStoryPoints(issue.getStoryPoints());
            sprintIssueRepository.save(si);
            if (!isDone) {
                if (nextSprint != null) {
                    SprintIssue moved = new SprintIssue();
                    moved.setId(new SprintIssueId(nextSprint.getSprintId(), issue.getIssueId()));
                    moved.setSprint(nextSprint);
                    moved.setIssue(issue);
                    moved.setAddedAt(LocalDateTime.now());
                    sprintIssueRepository.save(moved);
                    auditLogService.logAction("ISSUE_MOVED_BETWEEN_SPRINTS", EntityType.ISSUE, issue.getIssueId(),
                            "Chuyển issue " + issue.getIssueKey() + " từ sprint " + sprint.getSprintName() + " sang " + nextSprint.getSprintName());
                } else {
                    auditLogService.logAction("ISSUE_RETURNED_TO_BACKLOG", EntityType.ISSUE, issue.getIssueId(),
                            "Trả issue " + issue.getIssueKey() + " từ sprint " + sprint.getSprintName() + " về product backlog");
                }
            }
        }
        sprint.setState(SprintState.CLOSED);
        sprint.setCompletedAt(LocalDateTime.now());
        sprintRepository.save(sprint);
        auditLogService.logAction("SPRINT_COMPLETED", EntityType.SPRINT, sprintId,
                "Hoàn tất sprint " + sprint.getSprintName());
    }

    private Sprint getManageableSprint(int sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        return sprint;
    }

    private void requireFuture(Sprint sprint, String message) {
        if (sprint.getState() != SprintState.FUTURE) throw new RuntimeException(message);
    }

    private void validateDates(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new RuntimeException("Ngày kết thúc không được trước ngày bắt đầu");
        }
    }
}
