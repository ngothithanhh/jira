package com.example.jira.service.impl;

import com.example.jira.dto.project.CreateProjectRequest;
import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.dto.project.UpdateProjectRequest;
import com.example.jira.entity.Project;
import com.example.jira.entity.User;
import com.example.jira.mapper.ProjectMapper;
import com.example.jira.repository.ProjectRepository;
import com.example.jira.repository.ProjectStatsRepository;
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
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
    private final ProjectStatsRepository stats;
    private final com.example.jira.repository.UserRoleRepository roles;
    private final com.example.jira.repository.ProjectMemberRepository members;
    private final jakarta.persistence.EntityManager entityManager;

    /** User đang hoạt động tự tạo dự án; lead lấy từ JWT, ghi project và membership PROJECT_MANAGER cùng transaction. */
    @Override
    @Transactional
    public ProjectSummary createProject(CreateProjectRequest request){
        int userId = globalSecurity.currentUserId();
        // Khóa/refresh actor để tài khoản bị khóa trong lúc chờ không tạo project bằng trạng thái đã nạp cũ.
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(()->new NotFoundException("Không tìm thấy người dùng!"));
        entityManager.refresh(user);
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Tài khoản đã bị khóa");

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!globalSecurity.hasPermission(PermissionName.CREATE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền tạo project");

        // Kiểm trước khi lưu và trim khoảng trắng; không đổi case/regex key legacy hoặc nhận lead từ client.
        validateName(request == null ? null : request.getProjectName());
        validateDescription(request.getDescription());
        if (request.getProjectKey() == null || request.getProjectKey().isBlank() || request.getProjectKey().length() > 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã dự án cần từ 1 đến 10 ký tự");
        String key = request.getProjectKey().trim();
        if(projectRepository.existsByProjectKey(key)){
            throw new ConflictException("Khóa dự án đã tồn tại!");
        }
        // Mã role cố định do seed cung cấp; thiếu cấu hình thì dừng, không lưu một project không có thành viên chủ.
        var manager=roles.findByRoleName("PROJECT_MANAGER").orElseThrow(()->new ConflictException("Chưa có vai trò PROJECT_MANAGER"));

        Project project = new Project();
        project.setProjectName(request.getProjectName().trim());
        project.setProjectKey(key);
        project.setDescription(request.getDescription());
        project.setCreatedAt(LocalDateTime.now());

        project.setLead(user);
        projectRepository.saveAndFlush(project);
        // projectId vừa sinh và userId từ JWT tạo đúng membership chủ, không cấp role toàn hệ thống.
        var membership=new com.example.jira.entity.ProjectMember();
        membership.setId(new com.example.jira.entity.ProjectMemberId(project.getProjectId(),userId));
        membership.setProject(project);membership.setUser(user);membership.setRole(manager);membership.setAssignedAt(LocalDateTime.now());
        members.saveAndFlush(membership);
        auditLogService.logAction("CREATE_PROJECT",EntityType.PROJECT,project.getProjectId(),"Tạo dự án: "+key);
        var result=projectMapper.toResponse(project);
        attachCounts(List.of(result));
        return result;

    }

    /** Đọc danh sách projects và chỉ map các dự án lead/thành viên được VIEW_PROJECT; SYSTEM_ADMIN toàn cục xem mọi dự án. Không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummary> getProjects(){
        // Admin toàn cục xem được mọi dự án; người khác chỉ nhận project mình lead hoặc là thành viên.
        boolean systemAdmin = globalSecurity.isSystemAdmin();
        List<Project> projects = projectRepository.findAll();
        List<ProjectSummary> visible = projects.stream()
                .filter(project -> systemAdmin || projectSecurity.hasPermission(project.getProjectId(), PermissionName.VIEW_PROJECT))
                .map(projectMapper::toResponse).toList();
        attachCounts(visible);
        return visible;
    }

    /** id là ID projects trong path. Không có bản ghi thì 404, không được VIEW_PROJECT thì 403 trước khi trả DTO. Không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public ProjectSummary getProject(int id){
        Project project = projectRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        // id là ID projects từ path; chặn trước mapper để không lộ dữ liệu người ngoài.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(id, PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem dự án này");
        }
        ProjectSummary result = projectMapper.toResponse(project);
        attachCounts(List.of(result));
        return result;
    }

    /** id là ID projects cần đổi tên/mô tả. Thiếu UPDATE_PROJECT trên đúng dự án thì 403; giữ nguyên project_key và lead khi lưu. */
    @Override
    @Transactional
    public ProjectSummary updateProject(int id, UpdateProjectRequest request){
        int userId = SecurityUtils.getCurrentUserId();

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!projectSecurity.hasPermission(id, PermissionName.UPDATE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền sửa dự án này");

        Project project = projectRepository.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        // Chỉ sửa tên/mô tả sau quyền và tồn tại; key/lead cùng dữ liệu issue/sprint giữ nguyên.
        validateName(request == null ? null : request.getProjectName());
        validateDescription(request.getDescription());
        boolean changed = !java.util.Objects.equals(project.getProjectName(),request.getProjectName().trim())
                || !java.util.Objects.equals(project.getDescription(),request.getDescription());
        project.setProjectName(request.getProjectName().trim());
        project.setDescription(request.getDescription());
        projectRepository.save(project);
        // Giữ projectId trong lịch sử; cập nhật giống dữ liệu cũ không tạo audit thay đổi giả.
        if (changed) auditLogService.logAction("UPDATE_PROJECT",EntityType.PROJECT,id,"Cập nhật dự án");
        ProjectSummary result = projectMapper.toResponse(project);
        attachCounts(List.of(result));
        return result;
    }

    /** id thuộc projects cần xóa; khóa rồi kiểm lead/SYSTEM_ADMIN, dọn sole lead membership và xóa cùng audit trong transaction. */
    @Override
    @Transactional
    public void deleteProject(int id){
        // Khóa đúng project rồi kiểm chủ/lead hoặc SYSTEM_ADMIN; role PM ở project khác không tạo quyền xóa.
        Project project = projectRepository.findByIdForUpdate(id).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));
        entityManager.refresh(project);
        if(!projectSecurity.hasPermission(id,PermissionName.DELETE_PROJECT))
            throw new AccessDeniedException("Bạn không có quyền xóa project");

        // Chỉ dọn membership của lead khi đó là thành viên duy nhất. FK giữ dữ liệu công việc, lỗi rollback cả membership.
        var assigned=members.findByProject_ProjectId(id);
        if(assigned.stream().anyMatch(m->project.getLead()==null||m.getUser().getUserId()!=project.getLead().getUserId()))
            throw new ConflictException("Cần gỡ các thành viên khác trước khi xóa dự án");
        members.deleteAll(assigned);members.flush();
        projectRepository.delete(project);
        // Đẩy DELETE xuống DB trong cùng transaction để FK từ chối trước khi ghi audit/báo thành công.
        projectRepository.flush();
        
        auditLogService.logAction("DELETE", EntityType.PROJECT, id, "Xóa dự án: " + project.getProjectKey());
    }

    /** Chỉ đọc thống kê cho các projects vừa qua kiểm quyền; không đọc project khác và không ghi DB. */
    private void attachCounts(List<ProjectSummary> summaries) {
        Map<Integer, ProjectStatsRepository.Counts> counts = stats.read(summaries.stream().map(ProjectSummary::getProjectId).toList());
        for (var summary : summaries) {
            var value = counts.get(summary.getProjectId());
            if (value == null) continue;
            summary.setIssueCount(value.issues()); summary.setMemberCount(value.members());
            summary.setSprintCount(value.sprints()); summary.setActiveSprintName(value.activeSprintName());
        }
    }

    /** Tên từ form/API phải khớp giới hạn projects.project_name; caller xử lý lỗi400 trước save. */
    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên dự án cần từ 1 đến 100 ký tự");
    }

    /** description hiện là varchar255 trong projects; chặn trước save thay vì trả lỗi FK/trùng giả ở DB. */
    private void validateDescription(String description) {
        if (description != null && description.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả dự án tối đa 255 ký tự");
    }
}
