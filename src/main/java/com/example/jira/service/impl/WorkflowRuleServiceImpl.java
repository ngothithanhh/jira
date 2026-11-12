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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuleServiceImpl implements WorkflowRuleService {
    private final GlobalSecurity globalSecurity;

    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueStatusRepository issueStatusRepository;

    @Override
    @Transactional(readOnly = true)
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
    @Transactional
    public WorkflowRuleResponse createRule(WorkflowRuleRequest request) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        int fromId = request.getFromStatusId();
        int toId = request.getToStatusId();
        if (fromId == toId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái nguồn và đích phải khác nhau.");
        }

        // Cùng cặp nguồn/đích khóa hai hàng theo thứ tự ID trước khi kiểm trùng.
        int firstId = Math.min(fromId, toId);
        IssueStatus first = lockStatus(firstId, firstId == fromId);
        IssueStatus second = lockStatus(Math.max(fromId, toId), firstId != fromId);
        if (workflowRuleRepository.existsByFromStatus_StatusIdAndToStatus_StatusId(fromId, toId)) {
            throw new ConflictException("Quy tắc chuyển trạng thái này đã tồn tại!");
        }

        WorkflowRule rule = new WorkflowRule();
        rule.setFromStatus(firstId == fromId ? first : second);
        rule.setToStatus(firstId == fromId ? second : first);

        return WorkflowRuleMapper.toResponse(workflowRuleRepository.save(rule));
    }

    private IssueStatus lockStatus(int id, boolean source) {
        return issueStatusRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(source ? "Không tìm thấy trạng thái bắt đầu!" : "Không tìm thấy trạng thái đích!"));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. ruleId là ID workflow_rules; chỉ xóa quy tắc tìm được, không xóa issue_statuses nguồn/đích.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    @Transactional
    public void deleteRule(int ruleId) {
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        WorkflowRule rule = workflowRuleRepository.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quy tắc!"));
        workflowRuleRepository.delete(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueStatusResponse> getAllowedNextStatuses(int currentStatusId) {
        return workflowRuleRepository.findByFromStatus_StatusId(currentStatusId)
                .stream()
                .map(rule -> IssueStatusMapper.toResponse(rule.getToStatus()))
                .toList();
    }
}
