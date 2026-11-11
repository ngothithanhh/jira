package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.dto.workflow.WorkflowRuleRequest;
import com.example.jira.dto.workflow.WorkflowRuleResponse;
import com.example.jira.entity.IssueStatus;
import com.example.jira.entity.WorkflowRule;
import com.example.jira.mapper.IssueStatusMapper;
import com.example.jira.mapper.WorkflowRuleMapper;
import com.example.jira.repository.IssueStatusRepository;
import com.example.jira.repository.WorkflowRuleRepository;
import com.example.jira.service.WorkflowRuleService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuleServiceImpl implements WorkflowRuleService {
    private final GlobalSecurity globalSecurity;

    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueStatusRepository issueStatusRepository;

    @Override
    public List<WorkflowRuleResponse> getAllRules() {
        return workflowRuleRepository.findAll()
                .stream()
                .map(WorkflowRuleMapper::toResponse)
                .toList();
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. fromStatusId/toStatusId trong request là hai ID issue_statuses; lưu chiều chuyển vào workflow_rules sau khi kiểm tra tồn tại/trùng.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public WorkflowRuleResponse createRule(WorkflowRuleRequest request) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        if (workflowRuleRepository.existsByFromStatus_StatusIdAndToStatus_StatusId(request.getFromStatusId(), request.getToStatusId())) {
            throw new ConflictException("Quy tắc chuyển trạng thái này đã tồn tại!");
        }

        IssueStatus fromStatus = issueStatusRepository.findById(request.getFromStatusId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái bắt đầu!"));

        IssueStatus toStatus = issueStatusRepository.findById(request.getToStatusId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái đích!"));

        WorkflowRule rule = new WorkflowRule();
        rule.setFromStatus(fromStatus);
        rule.setToStatus(toStatus);

        return WorkflowRuleMapper.toResponse(workflowRuleRepository.save(rule));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. ruleId là ID workflow_rules; chỉ xóa quy tắc tìm được, không xóa issue_statuses nguồn/đích.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void deleteRule(int ruleId) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        WorkflowRule rule = workflowRuleRepository.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quy tắc!"));
        workflowRuleRepository.delete(rule);
    }

    @Override
    public List<IssueStatusResponse> getAllowedNextStatuses(int currentStatusId) {
        return workflowRuleRepository.findByFromStatus_StatusId(currentStatusId)
                .stream()
                .map(rule -> IssueStatusMapper.toResponse(rule.getToStatus()))
                .toList();
    }
}
