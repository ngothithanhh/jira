package com.example.jira.service.impl;

import com.example.jira.dto.project.CreateProjectRequest;
import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.dto.project.UpdateProjectRequest;
import com.example.jira.entity.Project;
import com.example.jira.entity.IssueStatus;
import com.example.jira.entity.User;
import com.example.jira.mapper.ProjectMapper;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.repository.IssueStatusRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.AuthService;
import com.example.jira.service.ProjectService;
import com.example.jira.enums.EntityType;
import com.example.jira.service.AuditLogService;
import com.example.jira.enums.PermissionName;
import com.example.jira.enums.StatusCategory;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final GlobalSecurity globalSecurity;
    private final ProjectSecurity projectSecurity;
    private final AuditLogService auditLogService;
    private final IssueStatusRepository issueStatusRepository;

    @Override
    public ProjectSummary createProject(CreateProjectRequest request){
        int userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(()->new RuntimeException("Không tìm thấy người dùng!"));

        if(!globalSecurity.hasPermission(PermissionName.CREATE_PROJECT))
            throw new RuntimeException("Bạn không có quyền tạo dự án");

        if(projectRepository.existsByProjectKey(request.getProjectKey())){
            throw new RuntimeException("Khóa dự án đã tồn tại!");
        }

        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setProjectKey(request.getProjectKey());
        project.setDescription(request.getDescription());
        project.setCreatedAt(LocalDateTime.now());
        IssueStatus defaultStatus = issueStatusRepository.findFirstByStatusCategoryOrderByStatusIdAsc(StatusCategory.TO_DO)
                .orElseThrow(() -> new RuntimeException("Chưa cấu hình trạng thái mặc định nhóm TO_DO!"));
        project.setDefaultIssueStatus(defaultStatus);

        project.setLead(user);
        projectRepository.save(project);

        return projectMapper.toResponse(project);

    }

    @Override
    public List<ProjectSummary> getProjects(){
        List<Project> projects = projectRepository.findAll();
        return projects.stream()
                .filter(project -> projectSecurity.hasPermission(project.getProjectId(), PermissionName.VIEW_PROJECT))
                .map(projectMapper::toResponse)
                .toList();
    }

    @Override
    public ProjectSummary getProject(int id){
        if (!projectSecurity.hasPermission(id, PermissionName.VIEW_PROJECT)) {
            throw new RuntimeException("Bạn không có quyền xem dự án này");
        }
        Project project = projectRepository.findById(id).orElseThrow(()->new RuntimeException("Không tìm thấy dự án!"));
        return projectMapper.toResponse(project);
    }

    @Override
    public ProjectSummary updateProject(int id, UpdateProjectRequest request){
        int userId = SecurityUtils.getCurrentUserId();

        if(!projectSecurity.hasPermission(id, PermissionName.UPDATE_PROJECT))
            throw new RuntimeException("Bạn không có quyền sửa dự án này");

        Project project = projectRepository.findById(id).orElseThrow(()->new RuntimeException("Không tìm thấy dự án!"));
        project.setProjectName(request.getProjectName());
        project.setDescription(request.getDescription());
        projectRepository.save(project);
        return projectMapper.toResponse(project);
    }

    @Override
    public void deleteProject(int id){
        int userId = SecurityUtils.getCurrentUserId();

        if(!globalSecurity.hasPermission(PermissionName.DELETE_PROJECT))
            throw new RuntimeException("Bạn không có quyền xóa dự án");

        Project project = projectRepository.findById(id).orElseThrow(()->new RuntimeException("Không tìm thấy dự án!"));
        projectRepository.delete(project);
        
        auditLogService.logAction("DELETE", EntityType.PROJECT, id, "Xóa dự án: " + project.getProjectKey());
    }
}
