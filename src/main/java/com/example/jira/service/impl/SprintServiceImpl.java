package com.example.jira.service.impl;

import com.example.jira.dto.sprint.CreateSprintRequest;
import com.example.jira.dto.sprint.SprintResponse;
import com.example.jira.entity.*;
import com.example.jira.enums.SprintState;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.SprintMapper;
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
        sprint.setCreatedAt(LocalDateTime.now());
        sprint.setState(SprintState.FUTURE);

        sprint = sprintRepository.save(sprint);
        return SprintMapper.toResponse(sprint);
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(int sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        return SprintMapper.toResponse(sprint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> getSprintsByProject(int projectId) {
        return sprintRepository.findByProject_ProjectId(projectId).stream()
                .map(SprintMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addIssueToSprint(int sprintId, int issueId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        Issue issue = issueRepository.findById(issueId).orElseThrow(() -> new RuntimeException("Không tìm thấy issue"));

        SprintIssue sprintIssue = new SprintIssue();
        sprintIssue.setId(new SprintIssueId(sprintId, issueId));
        sprintIssue.setSprint(sprint);
        sprintIssue.setIssue(issue);
        sprintIssue.setAddedAt(LocalDateTime.now());

        sprintIssueRepository.save(sprintIssue);
    }

    @Override
    @Transactional
    public void removeIssueFromSprint(int sprintId, int issueId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        sprintIssueRepository.deleteBySprint_SprintIdAndIssue_IssueId(sprintId, issueId);
    }

    @Override
    @Transactional
    public void startSprint(int sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        sprint.setState(SprintState.ACTIVE);
        sprintRepository.save(sprint);
    }

    @Override
    @Transactional
    public void completeSprint(int sprintId, Integer moveToSprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint"));
        if (!projectSecurity.hasPermission(sprint.getProject().getProjectId(), PermissionName.MANAGE_SPRINT)) {
            throw new RuntimeException("Không có quyền thực hiện");
        }
        sprint.setState(SprintState.CLOSED);
        sprintRepository.save(sprint);

        Sprint nextSprint = null;
        if (moveToSprintId != null) {
            nextSprint = sprintRepository.findById(moveToSprintId).orElseThrow(() -> new RuntimeException("Không tìm thấy sprint đích"));
        }

        List<SprintIssue> sprintIssues = sprintIssueRepository.findBySprint_SprintId(sprintId);
        for (SprintIssue si : sprintIssues) {
            Issue issue = si.getIssue();
            boolean isDone = issue.getStatus() != null && "Done".equalsIgnoreCase(issue.getStatus().getStatusName());
            if (!isDone) {
                removeIssueFromSprint(sprintId, issue.getIssueId());
                if (nextSprint != null) {
                    addIssueToSprint(nextSprint.getSprintId(), issue.getIssueId());
                }
            }
        }
    }
}
