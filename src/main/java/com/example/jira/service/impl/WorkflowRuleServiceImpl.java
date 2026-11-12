package com.example.jira.service.impl;
import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.dto.workflow.*;
import com.example.jira.entity.WorkflowRule;
import com.example.jira.mapper.*;
import com.example.jira.repository.*;
import com.example.jira.service.WorkflowRuleService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service @RequiredArgsConstructor
public class WorkflowRuleServiceImpl implements WorkflowRuleService {
    private final GlobalSecurity globalSecurity;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final IssueStatusRepository issueStatusRepository;

    /** Rule dùng chung toàn hệ thống; map hai status trong transaction để không phát sinh lazy-load lỗi khi OSIV=false. */
    @Override @Transactional(readOnly=true)
    public List<WorkflowRuleResponse> getAllRules(){return workflowRuleRepository.findAll().stream().map(WorkflowRuleMapper::toResponse).toList();}

    /** from/to là ID issue_statuses. Chỉ admin tạo cạnh; không sửa trạng thái issue khi thêm, unique DB chặn hai tạo trùng. */
    @Override @Transactional
    public WorkflowRuleResponse createRule(WorkflowRuleRequest request){
        globalSecurity.assertSystemAdmin();
        if(request==null||request.getFromStatusId()<=0||request.getToStatusId()<=0||request.getFromStatusId()==request.getToStatusId())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn hai trạng thái khác nhau và hợp lệ");
        int from=request.getFromStatusId(),to=request.getToStatusId();
        if(workflowRuleRepository.existsByFromStatus_StatusIdAndToStatus_StatusId(from,to))throw new ConflictException("Quy tắc chuyển trạng thái này đã tồn tại!");
        var source=issueStatusRepository.findById(from).orElseThrow(()->new NotFoundException("Không tìm thấy trạng thái bắt đầu"));
        var destination=issueStatusRepository.findById(to).orElseThrow(()->new NotFoundException("Không tìm thấy trạng thái đích"));
        var rule=new WorkflowRule();rule.setFromStatus(source);rule.setToStatus(destination);
        return WorkflowRuleMapper.toResponse(workflowRuleRepository.saveAndFlush(rule));
    }

    /** ruleId thuộc workflow_rules; chỉ xóa cạnh được xác nhận, không xóa issue/status/transition lịch sử. */
    @Override @Transactional
    public void deleteRule(int ruleId){
        globalSecurity.assertSystemAdmin();
        var rule=workflowRuleRepository.findById(ruleId).orElseThrow(()->new NotFoundException("Không tìm thấy quy tắc"));
        workflowRuleRepository.delete(rule);workflowRuleRepository.flush();
    }

    /** Issue editor/board đọc các đích được phép; không cần quyền sửa cấu hình toàn hệ thống. */
    @Override @Transactional(readOnly=true)
    public List<IssueStatusResponse> getAllowedNextStatuses(int currentStatusId){
        return workflowRuleRepository.findByFromStatus_StatusId(currentStatusId).stream().map(rule->IssueStatusMapper.toResponse(rule.getToStatus())).toList();
    }
}
