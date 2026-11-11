package com.example.jira.service.impl;

import com.example.jira.dto.issue.CreateIssueRequest;
import com.example.jira.dto.issue.UpdateIssueRequest;
import com.example.jira.dto.issue.issue_type.IssueTypeRequest;
import com.example.jira.dto.issue.issue_type.IssueTypeResponse;
import com.example.jira.entity.IssueType;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.IssueTypeMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.IssueTypeRepository;
import com.example.jira.repository.ProjectIssueTypeRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.IssueTypeService;
import com.example.jira.ultils.SecurityUtils;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueTypeServiceImpl implements IssueTypeService {
    private final IssueTypeRepository issueTypeRepository;
    private final GlobalSecurity globalSecurity;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final IssueRepository issueRepository;

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. Tạo tên loại chung trong issue_types từ request; cấu hình này dùng được cho nhiều dự án.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public IssueTypeResponse createIssueType(IssueTypeRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        int userId = SecurityUtils.getCurrentUserId();

        IssueType type = new IssueType();
        type.setTypeName(request.getTypeName());

        return IssueTypeMapper.toResponse(issueTypeRepository.save(type));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. id là ID issue_types cần đổi tên; không phải ID project_issue_types của một dự án.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public IssueTypeResponse updateIssueType(int id,IssueTypeRequest request){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        IssueType issueType = issueTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy Issue Type"));
        issueType.setTypeName(request.getTypeName());

        return IssueTypeMapper.toResponse(issueTypeRepository.save(issueType));
    }

    /**
     * Dùng khi admin sửa cấu hình toàn hệ thống. issueTypeId là ID issue_types; giữ kiểm tra project_issue_types và issues đang dùng trước khi xóa.
     * Người gọi phải có SYSTEM_ADMIN trong user_role_assignments; thiếu quyền thì 403 trước mọi đọc/ghi nghiệp vụ.
     */
    @Override
    public void deleteIssueType(int issueTypeId){
        // Chặn user thường trước khi tra ID mục tiêu; biết ID role/quyền không cho phép tự nâng quyền.
        globalSecurity.assertSystemAdmin();
        boolean usedInProjects = projectIssueTypeRepository.existsByIssueType_IssueTypeId(issueTypeId);
        if (usedInProjects) {
            throw new ConflictException("Không thể xóa Issue Type này vì đang được cấu hình sử dụng trong một số dự án.");
        }
        boolean hasIssues = issueRepository.existsByIssueType_IssueTypeId(issueTypeId);
        if (hasIssues) {
            throw new ConflictException("Không thể xóa vì đã có Task được tạo với Issue Type này.");
        }
        issueTypeRepository.deleteById(issueTypeId);

    }

    @Override
    public List<IssueTypeResponse> getAllIssueTypes() {
        return issueTypeRepository.findAll().stream()
                .map(IssueTypeMapper ::toResponse)
                .collect(Collectors.toList());
    }
}
