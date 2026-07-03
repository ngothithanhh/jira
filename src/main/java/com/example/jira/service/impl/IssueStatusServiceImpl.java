package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_status.IssueStatusRequest;
import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.entity.IssueStatus;
import com.example.jira.mapper.IssueStatusMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.IssueStatusRepository;
import com.example.jira.service.IssueStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueStatusServiceImpl implements IssueStatusService {

    private final IssueStatusRepository issueStatusRepository;
    private final IssueRepository issueRepository;

    @Override
    public List<IssueStatusResponse> getAllStatuses(){
        return issueStatusRepository.findAll().stream()
                .map(IssueStatusMapper ::toResponse)
                .collect(Collectors.toList());

    }

    @Override
    @Transactional
    public IssueStatusResponse createStatus(IssueStatusRequest request){

        if(issueStatusRepository.existByStatusName(request.getStatusName()))
            throw new RuntimeException("Tên trạng thái đã có!");

        IssueStatus status = new IssueStatus();
        status.setStatusName(request.getStatusName());
        status.setStatusCategory(request.getStatusCategory());

        return IssueStatusMapper.toResponse(issueStatusRepository.save(status));
    }

    @Override
    @Transactional
    public IssueStatusResponse updateStatus(int id, IssueStatusRequest request){
        IssueStatus status = issueStatusRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy trạng thái"));
        status.setStatusName(request.getStatusName());
        status.setStatusCategory(request.getStatusCategory());

        return IssueStatusMapper.toResponse(issueStatusRepository.save(status));
    }

    @Override
    @Transactional
    public void deleteStatus(int id){
        boolean inUsedInIssues = issueRepository.existByStatus_StatusId(id);

        if(inUsedInIssues)
            throw new RuntimeException("Không thể xóa trạng thái này vì đang có công việc sử dụng nó!");

        //check thêm bảng BoardColumnStatus
        // hoặc IssueTransition xem có cấu hình nào đang xài status này không.

        issueStatusRepository.deleteById(id);
    }
}
