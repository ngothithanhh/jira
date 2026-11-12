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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectIssueTypeServiceImpl implements ProjectIssueTypeService {
    private final ProjectSecurity projectSecurity;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectIssueTypeResponse> getProjectIssueTypes(int projectId){
        if (!projectRepository.existsById(projectId)) throw new NotFoundException("Không tìm thấy dự án!");
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
        Project project = lockAndAuthorize(projectId);

        IssueType issueType = issueTypeRepository.findById(issueTypeId).orElseThrow(()->new NotFoundException("Không tìm thấy loại nhiệm vụ!"));

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);

        if(projectIssueTypeRepository.existsById(id))
            throw new ConflictException("Đã được gán cho dự án rồi!");

        if (isDefault) {
            List<ProjectIssueType> existing = projectIssueTypeRepository.findByProject_ProjectId(projectId);
            existing.forEach(link -> link.setDefault(false));
        }
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
        lockAndAuthorize(projectId);
        List<ProjectIssueType> allTypesInProject = projectIssueTypeRepository.findByProject_ProjectId(projectId);
        if (allTypesInProject.stream().noneMatch(link -> link.getIssueType().getIssueTypeId() == issueTypeId)) {
            throw new NotFoundException("Bảng loại nhiệm vụ này chưa được gán vào dự án!");
        }

        allTypesInProject.forEach(link -> link.setDefault(link.getIssueType().getIssueTypeId() == issueTypeId));
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
        lockAndAuthorize(projectId);
        boolean hasIssues = issueRepository.existsByProject_ProjectIdAndIssueType_IssueTypeId(projectId,issueTypeId);
        if(hasIssues)
            throw new ConflictException("Không thể xóa vì đang được sử dụng!");

        ProjectIssueTypeId id = new ProjectIssueTypeId(projectId,issueTypeId);
        ProjectIssueType projectIssueType = projectIssueTypeRepository.findById(id).orElseThrow(()->new NotFoundException("Chưa được gán vào dự án!"));

        if(projectIssueType.isDefault())
            throw new ConflictException("Không thể gỡ bỏ vì đang được cấu hình là mặc định. Hãy chọn 1 loại khác làm mặc định trước!");

        projectIssueTypeRepository.deleteById(id);
    }

    /** Dùng cùng khóa project với tạo issue; đọc quyền sau khi lấy khóa để thấy membership vừa commit. */
    private Project lockAndAuthorize(int projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dự án!"));
        projectSecurity.requireIssueTypeManagement(projectId);
        return project;
    }
}
