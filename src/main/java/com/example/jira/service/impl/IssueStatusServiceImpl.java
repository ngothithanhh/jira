package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_status.IssueStatusRequest;
import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.entity.IssueStatus;
import com.example.jira.mapper.IssueStatusMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.IssueStatusRepository;
import com.example.jira.service.IssueStatusService;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueStatusServiceImpl implements IssueStatusService {
    private final GlobalSecurity globalSecurity;

    private final IssueStatusRepository issueStatusRepository;
    private final IssueRepository issueRepository;

    @Override
    public List<IssueStatusResponse> getAllStatuses(){
        return issueStatusRepository.findAll().stream()
                .map(IssueStatusMapper ::toResponse)
                .collect(Collectors.toList());

    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. Tạo tên/nhóm trạng thái chung trong issue_statuses từ request; giữ kiểm tra tên trùng.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    @Transactional
    public IssueStatusResponse createStatus(IssueStatusRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();

        String name = request.getStatusName().trim();
        if(issueStatusRepository.existsByStatusName(name))
            throw new ConflictException("Tên trạng thái đã có!");

        IssueStatus status = new IssueStatus();
        status.setStatusName(name);
        status.setStatusCategory(request.getStatusCategory());

        return IssueStatusMapper.toResponse(issueStatusRepository.save(status));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. id là ID issue_statuses cần đổi tên/nhóm; ảnh hưởng các issue dùng trạng thái này.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    @Transactional
    public IssueStatusResponse updateStatus(int id, IssueStatusRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        IssueStatus status = issueStatusRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái"));
        if (status.getStatusCategory() != request.getStatusCategory() && issueRepository.existsByStatus_StatusId(id)) {
            throw new ConflictException("Không thể đổi nhóm khi còn công việc sử dụng trạng thái này.");
        }
        status.setStatusName(request.getStatusName().trim());
        status.setStatusCategory(request.getStatusCategory());

        return IssueStatusMapper.toResponse(issueStatusRepository.save(status));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. id là ID issue_statuses cần xóa; giữ kiểm tra issues đang dùng trạng thái trước khi xóa.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    @Transactional
    public void deleteStatus(int id){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        issueStatusRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái"));
        boolean inUsedInIssues = issueRepository.existsByStatus_StatusId(id);

        if(inUsedInIssues)
            throw new ConflictException("Không thể xóa trạng thái này vì đang có công việc sử dụng nó!");

        // FK giữ board mapping, workflow và lịch sử chuyển trạng thái. Vi phạm được trả về 409.
        issueStatusRepository.deleteById(id);
    }
}
