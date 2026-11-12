package com.example.jira.service.impl;

import com.example.jira.dto.project.MemberCandidate;
import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.entity.*;
import com.example.jira.mapper.ProjectMemberMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.ProjectMemberService;
import com.example.jira.ultils.SecurityUtils;
import com.example.jira.enums.EntityType;
import com.example.jira.enums.PermissionName;
import com.example.jira.service.AuditLogService;
import com.example.jira.exception.NotFoundException;
import com.example.jira.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRoleRepository userRoleRepositoty;
    private final ProjectSecurity projectSecurity;
    private final AuditLogService auditLogService;
    private final jakarta.persistence.EntityManager entityManager;

    /** projectId là ID projects trong path. Thiếu VIEW_PROJECT thì 403 trước truy vấn project_members; đủ quyền mới trả danh sách, không ghi DB. */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(int projectId){
        requireProject(projectId);
        // projectId là ID projects từ path, không phải ID user; kiểm quyền trước khi đọc project_members.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem thành viên của dự án này");
        }
        List<ProjectMember> members = projectMemberRepository.findByProject_ProjectId(projectId);

        return members.stream().map(ProjectMemberMapper::toResponse).toList();
    }

    /** Tra đúng email, chỉ sau khi xác minh quyền thêm trên project của URL. */
    @Override
    @Transactional(readOnly = true)
    public List<MemberCandidate> findCandidate(int projectId, String email) {
        requireProject(projectId);
        projectSecurity.requirePermission(projectId, PermissionName.ADD_MEMBER);
        return userRepository.findProjectCandidate(projectId, email).stream().toList();
    }

    /** projectId/userId là cặp khóa project_members cần gỡ, khác ID người gọi. Thiếu DELETE_MEMBER thì 403; đủ quyền mới xóa đúng cặp và ghi audit. */
    @Override
    @Transactional
    public void removeMember(int projectId, int userId){
        Project project = lockProject(projectId);
        if(!projectSecurity.hasPermission(projectId, PermissionName.DELETE_MEMBER))
            throw new AccessDeniedException("Bạn không có quyền xóa thành viên khỏi dự án này!");

        if (project.getLead()!=null && project.getLead().getUserId()==userId) throw new ConflictException("Không gỡ lead khi projects vẫn chỉ định người này");
        ProjectMemberId id = new ProjectMemberId(projectId,userId);
        if (!projectMemberRepository.existsById(id)) throw new NotFoundException("Không tìm thấy thành viên");
        projectMemberRepository.deleteById(id);
        
        auditLogService.logAction("REMOVE_MEMBER", EntityType.PROJECT, projectId, "Xóa người dùng ID " + userId + " khỏi dự án");
    }

    /**
     * Đổi role của cặp project_members theo projectId/userId trên URL; roleId là user_roles, không phải gán toàn cục.
     * Khóa project rồi kiểm ADD_MEMBER, member/role/PM khác trước ghi; giữ assigned_at.
     * Không cho SYSTEM_ADMIN làm role project mới; lỗi audit rollback role, không tự retry.
     */
    @Override @Transactional
    public void updateRole(int projectId,int userId,int roleId) {
        lockProject(projectId);
        projectSecurity.requirePermission(projectId, PermissionName.ADD_MEMBER);
        ProjectMember member = projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId,userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thành viên"));
        entityManager.refresh(member);
        UserRole role = userRoleRepositoty.findByRoleId(roleId).orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        requireProjectRole(role);
        if ("PROJECT_MANAGER".equalsIgnoreCase(role.getRoleName()) && projectMemberRepository.countOtherProjectManagers(projectId,userId)>0) throw new ConflictException("Đã có PROJECT MANAGER!");
        member.setRole(role); projectMemberRepository.save(member);
        auditLogService.logAction("UPDATE_MEMBER_ROLE",EntityType.PROJECT,projectId,"Đổi role user ID " + userId + " sang role ID " + roleId);
    }

    /**
     * Dùng trước add/remove/updateRole. Khóa projects rồi refresh lead và membership của actor đã xác thực.
     * Controller/OEIV có thể đã nạp role cũ trước khi chờ khóa; query lại không tự thay entity đang managed.
     * Refresh đúng cặp projectId/actorId để kiểm quyền theo role đã commit, không mượn role của member đích.
     * Hàm chỉ đọc/khóa; caller kiểm ADD_MEMBER hoặc DELETE_MEMBER sau đó, thiếu quyền dừng trước ghi.
     */
    private Project lockProject(int projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId).orElseThrow(() -> new NotFoundException("Không tìm thấy dự án!"));
        entityManager.refresh(project);
        // Chỉ làm mới actor thuộc project đang khóa; nếu đã bị gỡ thì query rỗng và kiểm quyền tiếp theo sẽ từ chối.
        int actorId=SecurityUtils.getCurrentUserId();
        projectMemberRepository.findByProject_ProjectIdAndUser_UserId(projectId,actorId).ifPresent(entityManager::refresh);
        return project;
    }

    private void requireProject(int projectId) {
        if (!projectRepository.existsById(projectId)) throw new NotFoundException("Không tìm thấy dự án!");
    }
    /** SYSTEM_ADMIN chỉ có nghĩa toàn cục, không gán mới vào project_members để tránh nhầm quyền. */
    private void requireProjectRole(UserRole role) {
        if ("SYSTEM_ADMIN".equalsIgnoreCase(role.getRoleName())) throw new ConflictException("SYSTEM_ADMIN không phải vai trò thành viên dự án");
    }
}
