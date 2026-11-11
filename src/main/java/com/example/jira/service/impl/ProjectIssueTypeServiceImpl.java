package com.example.jira.service.impl;

import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.entity.IssueType;
import com.example.jira.entity.Project;
import com.example.jira.entity.ProjectIssueType;
import com.example.jira.entity.ProjectIssueTypeId;
import com.example.jira.mapper.ProjectIssueTypeMapper;
import com.example.jira.repository.IssueRepository;
import com.example.jira.repository.IssueTypeRepository;
import com.example.jira.repository.ProjectIssueTypeRepository;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.service.ProjectIssueTypeService;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.enums.PermissionName;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class ProjectIssueTypeServiceImpl implements ProjectIssueTypeService {
    private final ProjectSecurity projectSecurity;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;

    @Override
    public List<ProjectIssueTypeResponse> getProjectIssueTypes(int projectId){
        // Không đọc danh sách loại nhiệm vụ cho người ngoài dự án.
        projectSecurity.requirePermission(projectId, PermissionName.VIEW_PROJECT);

        List<ProjectIssueType> pt = projectIssueTypeRepository.findByProject_ProjectId(projectId);

        return pt.stream().map(ProjectIssueTypeMapper::toResponse).toList();

    }

    /**
     * Gán issueTypeId (ID issue_types) vào projectId (ID projects) trong project_issue_types.
     * Chỉ lead/admin hoặc thành viên có MANAGE_BOARD/UPDATE_PROJECT trên chính dự án mới được thay đổi.
     * Thiếu quyền thì 403 trước khi đọc/ghi cấu hình; giữ nguyên các kiểm tra và cập nhật nghiệp vụ cũ.
     */
    @Override
    @Transactional
    public void assignIssueType(int projectId, int issueTypeId, boolean isDefault){
        // Chặn trước mọi cập nhật, kể cả đổi is_default trên entity đang được JPA theo dõi.
        projectSecurity.requireIssueTypeManagement(projectId);
        Project project = projectRepository.findById(projectId).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));

        IssueType issueType = issueTypeRepository.findById(issueTypeId).orElseThrow(()->new NotFoundException("Không tìm thấy loại nhiệm vụ!"));

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);

        if(projectIssueTypeRepository.existsById(id))
            throw new ConflictException("Đã được gán cho dự án rồi!");

        ProjectIssueType projectIssueType = new ProjectIssueType(id, project, issueType, isDefault);
        projectIssueTypeRepository.save(projectIssueType);
    }

    /**
     * Đổi is_default của các project_issue_types thuộc projectId sang issueTypeId đã được gán.
     * Chỉ lead/admin hoặc thành viên có MANAGE_BOARD/UPDATE_PROJECT trên chính dự án mới được thay đổi.
     * Thiếu quyền thì 403 trước khi đọc/ghi cấu hình; giữ nguyên các kiểm tra và cập nhật nghiệp vụ cũ.
     */
    @Override
    @Transactional
    public void setDefaultIssueType(int projectId, int issueTypeId){
        // Chặn trước mọi cập nhật, kể cả đổi is_default trên entity đang được JPA theo dõi.
        projectSecurity.requireIssueTypeManagement(projectId);
        List<ProjectIssueType> allTypesInProject = projectIssueTypeRepository.findByProject_ProjectId(projectId);

        boolean found = false;
        for(ProjectIssueType projectIssueType : allTypesInProject){
            if(projectIssueType.getIssueType().getIssueTypeId() == issueTypeId){
                found = true;
                projectIssueType.setDefault(true);
            }
            else {
                projectIssueType.setDefault(false);
            }
        }

        if(!found){
            throw new NotFoundException("Bảng loại nhiệm vụ này chưa được gán vào dự án!");
        }

        projectIssueTypeRepository.saveAll(allTypesInProject);


    }

    /**
     * Gỡ cặp projectId/issueTypeId trong project_issue_types sau khi kiểm tra issue và loại mặc định.
     * Chỉ lead/admin hoặc thành viên có MANAGE_BOARD/UPDATE_PROJECT trên chính dự án mới được thay đổi.
     * Thiếu quyền thì 403 trước khi đọc/ghi cấu hình; giữ nguyên các kiểm tra và cập nhật nghiệp vụ cũ.
     */
    @Override
    @Transactional
    public void removeIssueType(int projectId, int issueTypeId){
        // Chặn trước mọi cập nhật, kể cả đổi is_default trên entity đang được JPA theo dõi.
        projectSecurity.requireIssueTypeManagement(projectId);
        boolean hasIssues = issueRepository.existsByProject_ProjectIdAndIssueType_IssueTypeId(projectId,issueTypeId);
        if(hasIssues)
            throw new ConflictException("Không thể xóa vì đang được sử dụng!");

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);
        ProjectIssueType projectIssueType = projectIssueTypeRepository.findById(id).orElseThrow(()->new NotFoundException("Chưa được gán vào dự án!"));

        if(projectIssueType.isDefault())
            throw new ConflictException("Không thể gỡ bỏ vì đang được cấu hình là mặc định. Hãy chọn 1 loại khác làm mặc định trước!");

        projectIssueTypeRepository.deleteById(id);
    }
}
