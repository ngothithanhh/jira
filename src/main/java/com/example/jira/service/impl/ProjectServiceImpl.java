package com.example.jira.service.impl;

import com.example.jira.dto.project.CreateProjectRequest;
import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.dto.project.UpdateProjectRequest;
import com.example.jira.entity.Project;
import com.example.jira.entity.User;
import com.example.jira.mapper.ProjectMapper;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.AuthService;
import com.example.jira.service.ProjectService;
import com.example.jira.enums.EntityType;
import com.example.jira.service.AuditLogService;
import com.example.jira.enums.PermissionName;
import com.example.jira.ultils.SecurityUtils;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
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

    /** Người gọi tạo dự án; ID lead lấy từ UserPrincipal và tra users. Kiểm CREATE_PROJECT trước khi lưu projects; trùng project_key thì 409. */
    @Override
    public ProjectSummary createProject(CreateProjectRequest request){
        int userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(()->new NotFoundException("Không tìm thấy người dùng!"));

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!globalSecurity.hasPermission(PermissionName.CREATE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền tạo project");

        if(projectRepository.existsByProjectKey(request.getProjectKey())){
            throw new ConflictException("Khóa dự án đã tồn tại!");
        }

        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setProjectKey(request.getProjectKey());
        project.setDescription(request.getDescription());
        project.setCreatedAt(LocalDateTime.now());

        project.setLead(user);
        projectRepository.save(project);

        return projectMapper.toResponse(project);

    }

    /** Đọc danh sách projects và chỉ map các dự án lead/thành viên được VIEW_PROJECT; SYSTEM_ADMIN toàn cục xem mọi dự án. Không ghi DB. */
    @Override
    public List<ProjectSummary> getProjects(){
        // Admin toàn cục xem được mọi dự án; người khác chỉ nhận project mình lead hoặc là thành viên.
        boolean systemAdmin = globalSecurity.isSystemAdmin();
        List<Project> projects = projectRepository.findAll();
        return projects.stream()
                .filter(project -> systemAdmin || projectSecurity.hasPermission(project.getProjectId(), PermissionName.VIEW_PROJECT))
                .map(projectMapper::toResponse).toList();
    }

    /** id là ID projects trong path. Không có bản ghi thì 404, không được VIEW_PROJECT thì 403 trước khi trả DTO. Không ghi DB. */
    @Override
    public ProjectSummary getProject(int id){
        Project project = projectRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        // id là ID projects từ path; chặn trước mapper để không lộ dữ liệu người ngoài.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(id, PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem dự án này");
        }
        return projectMapper.toResponse(project);
    }

    /** id là ID projects cần đổi tên/mô tả. Thiếu UPDATE_PROJECT trên đúng dự án thì 403; giữ nguyên project_key và lead khi lưu. */
    @Override
    public ProjectSummary updateProject(int id, UpdateProjectRequest request){
        int userId = SecurityUtils.getCurrentUserId();

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!projectSecurity.hasPermission(id, PermissionName.UPDATE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền sửa dự án này");

        Project project = projectRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        project.setProjectName(request.getProjectName());
        project.setDescription(request.getDescription());
        projectRepository.save(project);
        return projectMapper.toResponse(project);
    }

    /** id là ID projects cần xóa; giữ kiểm DELETE_PROJECT toàn cục, thiếu quyền thì 403. Xóa thành công mới ghi audit theo ID dự án. */
    @Override
    public void deleteProject(int id){
        int userId = SecurityUtils.getCurrentUserId();

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!globalSecurity.hasPermission(PermissionName.DELETE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền xóa project");

        Project project = projectRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        projectRepository.delete(project);
        
        auditLogService.logAction("DELETE", EntityType.PROJECT, id, "Xóa dự án: " + project.getProjectKey());
    }
}
