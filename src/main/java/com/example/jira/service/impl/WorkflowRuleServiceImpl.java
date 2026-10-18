package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.dto.workflow.WorkflowRuleRequest;
import com.example.jira.dto.workflow.WorkflowRuleResponse;
import com.example.jira.entity.IssueStatus;
import com.example.jira.entity.Project;
import com.example.jira.entity.WorkflowRule;
import com.example.jira.mapper.IssueStatusMapper;
import com.example.jira.mapper.WorkflowRuleMapper;
import com.example.jira.repository.IssueStatusRepository;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.WorkflowRuleRepository;
import com.example.jira.service.WorkflowRuleService;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.enums.PermissionName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuleServiceImpl implements WorkflowRuleService {

    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final ProjectRepository projectRepository;
    private final ProjectSecurity projectSecurity;

    @Override
    public List<WorkflowRuleResponse> getAllRules(int projectId) {
        requirePermission(projectId, PermissionName.VIEW_PROJECT);
        return workflowRuleRepository.findByProject_ProjectId(projectId)
                .stream()
                .map(WorkflowRuleMapper::toResponse)
                .toList();
    }

    @Override
    public WorkflowRuleResponse createRule(int projectId, WorkflowRuleRequest request) {
        requirePermission(projectId, PermissionName.MANAGE_WORKFLOW);
        if (workflowRuleRepository.existsByProject_ProjectIdAndFromStatus_StatusIdAndToStatus_StatusId(projectId, request.getFromStatusId(), request.getToStatusId())) {
            throw new RuntimeException("Quy tắc chuyển trạng thái này đã tồn tại!");
        }

        IssueStatus fromStatus = issueStatusRepository.findById(request.getFromStatusId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái bắt đầu!"));

        IssueStatus toStatus = issueStatusRepository.findById(request.getToStatusId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái đích!"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dự án"));

        WorkflowRule rule = new WorkflowRule();
        rule.setProject(project);
        rule.setFromStatus(fromStatus);
        rule.setToStatus(toStatus);

        return WorkflowRuleMapper.toResponse(workflowRuleRepository.save(rule));
    }

    @Override
    public void deleteRule(int projectId, int ruleId) {
        requirePermission(projectId, PermissionName.MANAGE_WORKFLOW);
        WorkflowRule rule = workflowRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quy tắc!"));
        if (rule.getProject().getProjectId() != projectId) {
            throw new RuntimeException("Quy tắc quy trình không thuộc dự án này");
        }
        workflowRuleRepository.delete(rule);
    }

    @Override
    public List<IssueStatusResponse> getAllowedNextStatuses(int projectId, int currentStatusId) {
        requirePermission(projectId, PermissionName.VIEW_PROJECT);
        return workflowRuleRepository.findByProject_ProjectIdAndFromStatus_StatusId(projectId, currentStatusId)
                .stream()
                .map(rule -> IssueStatusMapper.toResponse(rule.getToStatus()))
                .toList();
    }

    private void requirePermission(int projectId, PermissionName permission) {
        if (!projectSecurity.hasPermission(projectId, permission)) {
            throw new RuntimeException("Bạn không có quyền thay đổi quy trình trong dự án này!");
        }
    }
}
