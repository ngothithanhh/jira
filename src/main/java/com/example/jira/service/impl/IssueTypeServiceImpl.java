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

    @Override
    public IssueTypeResponse createIssueType(IssueTypeRequest request){
        int userId = SecurityUtils.getCurrentUserId();

        IssueType type = new IssueType();
        type.setTypeName(request.getTypeName());

        return IssueTypeMapper.toResponse(issueTypeRepository.save(type));
    }

    @Override
    public IssueTypeResponse updateIssueType(int id,IssueTypeRequest request){
        IssueType issueType = issueTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Issue Type"));
        issueType.setTypeName(request.getTypeName());

        return IssueTypeMapper.toResponse(issueTypeRepository.save(issueType));
    }

    @Override
    public void deleteIssueType(int issueTypeId){
        boolean usedInProjects = projectIssueTypeRepository.existsByIssueType_IssueTypeId(issueTypeId);
        if (usedInProjects) {
            throw new RuntimeException("Không thể xóa Issue Type này vì đang được cấu hình sử dụng trong một số dự án.");
        }
        boolean hasIssues = issueRepository.existsByIssueType_IssueTypeId(issueTypeId);
        if (hasIssues) {
            throw new RuntimeException("Không thể xóa vì đã có Task được tạo với Issue Type này.");
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
