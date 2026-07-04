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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuleServiceImpl implements WorkflowRuleService {

    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueStatusRepository issueStatusRepository;

    @Override
    public List<WorkflowRuleResponse> getAllRules() {
        return workflowRuleRepository.findAll()
                .stream()
                .map(WorkflowRuleMapper::toResponse)
                .toList();
    }

    @Override
    public WorkflowRuleResponse createRule(WorkflowRuleRequest request) {
        if (workflowRuleRepository.existsByFromStatus_StatusIdAndToStatus_StatusId(request.getFromStatusId(), request.getToStatusId())) {
            throw new RuntimeException("Quy tắc chuyển trạng thái này đã tồn tại!");
        }

        IssueStatus fromStatus = issueStatusRepository.findById(request.getFromStatusId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái bắt đầu!"));

        IssueStatus toStatus = issueStatusRepository.findById(request.getToStatusId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái đích!"));

        WorkflowRule rule = new WorkflowRule();
        rule.setFromStatus(fromStatus);
        rule.setToStatus(toStatus);

        return WorkflowRuleMapper.toResponse(workflowRuleRepository.save(rule));
    }

    @Override
    public void deleteRule(int ruleId) {
        WorkflowRule rule = workflowRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quy tắc!"));
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
