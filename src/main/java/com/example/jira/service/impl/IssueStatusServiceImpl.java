package com.example.jira.service.impl;
import com.example.jira.dto.issue.issue_status.*;
import com.example.jira.entity.IssueStatus;
import com.example.jira.mapper.IssueStatusMapper;
import com.example.jira.repository.*;
import com.example.jira.service.IssueStatusService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor
public class IssueStatusServiceImpl implements IssueStatusService {
    private final GlobalSecurity globalSecurity;
    private final IssueStatusRepository issueStatusRepository;
    private final IssueRepository issueRepository;
    private final BoardColumnStatusRepository mappings;
    private final WorkflowRuleRepository rules;
    private final IssueTransitionRepository transitions;

    /** Chỉ đọc danh mục chung; category từ DB quyết định ý nghĩa TODO/đang làm/hoàn thành. */
    @Override @Transactional(readOnly=true)
    public List<IssueStatusResponse> getAllStatuses(){return issueStatusRepository.findAll().stream().map(IssueStatusMapper::toResponse).toList();}

    /** Admin chọn nhóm khi tạo; tên duy nhất theo DB, không tự đổi nhóm của status khác để tái sử dụng. */
    @Override @Transactional
    public IssueStatusResponse createStatus(IssueStatusRequest request){
        globalSecurity.assertSystemAdmin();String name=validate(request);
        if(issueStatusRepository.existsByStatusName(name))throw new ConflictException("Tên trạng thái đã có!");
        var row=new IssueStatus();row.setStatusName(name);row.setStatusCategory(request.getStatusCategory());
        return IssueStatusMapper.toResponse(issueStatusRepository.saveAndFlush(row));
    }

    /** Nhóm được chọn khi tạo và giữ nguyên: PUT chỉ đổi tên, khác category409 trước sửa để bảo toàn ý nghĩa lịch sử Scrum. */
    @Override @Transactional
    public IssueStatusResponse updateStatus(int id,IssueStatusRequest request){
        globalSecurity.assertSystemAdmin();String name=validate(request);
        var row=issueStatusRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy trạng thái"));
        if(row.getStatusCategory()!=request.getStatusCategory())throw new ConflictException("Nhóm trạng thái cố định sau khi tạo");
        if(issueStatusRepository.existsByStatusNameAndStatusIdNot(name,id))throw new ConflictException("Tên trạng thái đã có!");
        row.setStatusName(name);return IssueStatusMapper.toResponse(issueStatusRepository.saveAndFlush(row));
    }

    /** id là issue_statuses.status_id. Không xóa mapping, rule hoặc lịch sử hộ người dùng; các FK vẫn chặn race cuối transaction. */
    @Override @Transactional
    public void deleteStatus(int id){
        globalSecurity.assertSystemAdmin();
        issueStatusRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy trạng thái"));
        if(issueRepository.existsByStatus_StatusId(id))throw new ConflictException("Trạng thái đang có công việc sử dụng");
        if(mappings.existsByIssueStatus_StatusId(id)||rules.existsByFromStatus_StatusIdOrToStatus_StatusId(id,id)
            ||transitions.existsByFromStatus_StatusIdOrToStatus_StatusId(id,id))
            throw new ConflictException("Trạng thái còn mapping, workflow hoặc lịch sử sử dụng");
        issueStatusRepository.deleteById(id);issueStatusRepository.flush();
    }

    /** Dữ liệu body cần tên1..50 và một nhóm enum; không lưu null/rỗng từ caller nội bộ. */
    private String validate(IssueStatusRequest request){
        if(request==null||request.getStatusName()==null||request.getStatusName().trim().isEmpty()||request.getStatusName().trim().length()>50||request.getStatusCategory()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên trạng thái cần 1..50 ký tự và nhóm hợp lệ");
        return request.getStatusName().trim();
    }
}
