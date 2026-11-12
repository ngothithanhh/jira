package com.example.jira.service.impl;
import com.example.jira.dto.issue.issue_type.ProjectIssueTypeResponse;
import com.example.jira.entity.*;
import com.example.jira.mapper.ProjectIssueTypeMapper;
import com.example.jira.repository.*;
import com.example.jira.service.ProjectIssueTypeService;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.enums.PermissionName;
import com.example.jira.exception.*;
import com.example.jira.ultils.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProjectIssueTypeServiceImpl implements ProjectIssueTypeService {
    private final ProjectSecurity projectSecurity;
    private final ProjectIssueTypeRepository projectIssueTypeRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final EntityManager entityManager;

    /** Đọc cặp project/issue_type trong transaction để mapper không phụ thuộc OpenEntityManagerInView. */
    @Override @Transactional(readOnly=true)
    public List<ProjectIssueTypeResponse> getProjectIssueTypes(int projectId){
        projectSecurity.requirePermission(projectId,PermissionName.VIEW_PROJECT);
        return projectIssueTypeRepository.findByProject_ProjectId(projectId).stream().map(ProjectIssueTypeMapper::toResponse).toList();
    }

    /** Gán issue_types.issue_type_id vào projects.project_id; default=true đổi cờ cũ trong cùng transaction đã khóa project. */
    @Override @Transactional
    public void assignIssueType(int projectId,int issueTypeId,boolean isDefault){
        Project project=lockProject(projectId);
        var type=issueTypeRepository.findById(issueTypeId).orElseThrow(()->new NotFoundException("Không tìm thấy loại nhiệm vụ"));
        var id=new ProjectIssueTypeId(projectId,issueTypeId);
        if(projectIssueTypeRepository.existsById(id))throw new ConflictException("Đã được gán cho dự án rồi!");
        if(isDefault)clearDefaults(projectId);
        projectIssueTypeRepository.saveAndFlush(new ProjectIssueType(id,project,type,isDefault));
    }

    /** Tìm target trước đổi cờ; flush false trước true để unique partial index luôn giữ tối đa một default. */
    @Override @Transactional
    public void setDefaultIssueType(int projectId,int issueTypeId){
        lockProject(projectId);
        var rows=projectIssueTypeRepository.findByProject_ProjectId(projectId);
        var target=rows.stream().filter(row->row.getIssueType().getIssueTypeId()==issueTypeId).findFirst()
            .orElseThrow(()->new NotFoundException("Loại nhiệm vụ chưa được gán vào dự án"));
        if(target.isDefault())return;
        clearDefaults(projectId);
        target.setDefault(true);projectIssueTypeRepository.saveAndFlush(target);
    }

    /** Gỡ đúng cặp khóa; loại mặc định hoặc còn issue trong chính project trả409, không thay loại của issue. */
    @Override @Transactional
    public void removeIssueType(int projectId,int issueTypeId){
        lockProject(projectId);
        var id=new ProjectIssueTypeId(projectId,issueTypeId);
        var row=projectIssueTypeRepository.findById(id).orElseThrow(()->new NotFoundException("Chưa được gán vào dự án"));
        if(row.isDefault())throw new ConflictException("Hãy chọn loại mặc định khác trước khi gỡ");
        if(issueRepository.existsByProject_ProjectIdAndIssueType_IssueTypeId(projectId,issueTypeId))throw new ConflictException("Loại công việc đang được sử dụng");
        // Project đã refresh có collection cascade ALL; bulk delete tránh Hibernate persist lại child đã remove.
        if(projectIssueTypeRepository.deleteScoped(projectId,issueTypeId)==0)throw new NotFoundException("Chưa được gán vào dự án");
    }

    /** Khóa projects trước gán/default/gỡ và refresh actor membership; role cũ khi chờ không được dùng để ghi. */
    private Project lockProject(int projectId){
        projectSecurity.requireIssueTypeManagement(projectId);
        var project=projectRepository.findByIdForUpdate(projectId).orElseThrow(()->new NotFoundException("Không tìm thấy dự án"));
        entityManager.refresh(project);
        var member=entityManager.find(ProjectMember.class,new ProjectMemberId(projectId,SecurityUtils.getCurrentUserId()));
        if(member!=null)try{entityManager.refresh(member);}catch(jakarta.persistence.EntityNotFoundException removed){throw new org.springframework.security.access.AccessDeniedException("Bạn không còn là thành viên dự án");}
        projectSecurity.requireIssueTypeManagement(projectId);
        return project;
    }

    /** Chỉ đổi các cờ true thuộc project đã khóa; flush trước ghi default mới, lỗi sẽ rollback toàn bộ transaction. */
    private void clearDefaults(int projectId){
        var rows=projectIssueTypeRepository.findByProject_ProjectId(projectId);
        rows.forEach(row->row.setDefault(false));
        projectIssueTypeRepository.saveAll(rows);projectIssueTypeRepository.flush();
    }
}
