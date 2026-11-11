package com.example.jira.service.impl;

import com.example.jira.dto.project.AddMemberRequest;
import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.entity.*;
import com.example.jira.mapper.ProjectMemberMapper;
import com.example.jira.repository.*;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.ProjectMemberService;
import com.example.jira.service.ProjectService;
import com.example.jira.enums.EntityType;
import com.example.jira.enums.NotificationType;
import com.example.jira.enums.PermissionName;
import com.example.jira.service.AuditLogService;
import com.example.jira.service.NotificationService;
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
public class ProjectMemberServiceImpl implements ProjectMemberService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRoleRepository userRoleRepositoty;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ProjectSecurity projectSecurity;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    /** projectId là ID projects, request.userId là người được thêm và roleId là ID user_roles. Kiểm ADD_MEMBER trước khi đọc/ghi; lưu project_members rồi audit và thông báo người được thêm. */
    @Override
    public void addMember(int projectId, AddMemberRequest request){

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!projectSecurity.hasPermission(projectId, PermissionName.ADD_MEMBER))
            throw new AccessDeniedException("Bạn không có quyền thêm thành viên vào dự án này!");

        Project project = projectRepository.findById(projectId).orElseThrow(()->new NotFoundException("Không tìm thấy dự án!"));

        User user = userRepository.findById(request.getUserId()).orElseThrow(()->new NotFoundException("Không tìm thấy người dùng!"));

        UserRole userRole = userRoleRepositoty.findByRoleId(request.getRoleId()).orElseThrow(()->new NotFoundException("Không tìm thấy vai trò!"));

        if(userRole.getRoleName().equals("PROJECT_MANAGER")){
            if(projectMemberRepository.existsByProject_ProjectIdAndRole_RoleName(projectId,"PROJECT_MANAGER")){
                throw new ConflictException("Đã có PROJECT MANAGER!");
            }
        }

        ProjectMemberId id = new ProjectMemberId(projectId,user.getUserId());

        if(projectMemberRepository.existsById(id)){
            throw new ConflictException("Người dùng đã có trong dự án!");
        }

        ProjectMember member = new ProjectMember();
        member.setId(id);
        member.setProject(project);
        member.setRole(userRole);
        member.setUser(user);
        member.setAssignedAt(LocalDateTime.now());

        projectMemberRepository.save(member);
        
        auditLogService.logAction("ADD_MEMBER", EntityType.PROJECT, projectId, "Thêm người dùng ID " + request.getUserId() + " với vai trò ID " + request.getRoleId());
        
        int actorId = SecurityUtils.getCurrentUserId();
        User actor = userRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Hệ thống";
        String message = actorName + " đã thêm bạn vào dự án " + project.getProjectName() + ".";
        notificationService.sendNotification(request.getUserId(), null, actorId, NotificationType.PROJECT_INVITED, message);
    }
    /** projectId là ID projects trong path. Thiếu VIEW_PROJECT thì 403 trước truy vấn project_members; đủ quyền mới trả danh sách, không ghi DB. */
    @Override
    public List<ProjectMemberResponse> getMembers(int projectId){
        // projectId là ID projects từ path, không phải ID user; kiểm quyền trước khi đọc project_members.
        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if (!projectSecurity.hasPermission(projectId, PermissionName.VIEW_PROJECT)) {
            throw new AccessDeniedException("Bạn không có quyền xem thành viên của dự án này");
        }
        List<ProjectMember> members = projectMemberRepository.findByProject_ProjectId(projectId);

        return members.stream().map(ProjectMemberMapper::toResponse).toList();
    }

    /** projectId/userId là cặp khóa project_members cần gỡ, khác ID người gọi. Thiếu DELETE_MEMBER thì 403; đủ quyền mới xóa đúng cặp và ghi audit. */
    @Override
    public void removeMember(int projectId, int userId){

        // Thiếu quyền trên bên sở hữu dữ liệu thì dừng bằng 403, không thực hiện các cập nhật bên dưới.
        if(!projectSecurity.hasPermission(projectId, PermissionName.DELETE_MEMBER))
            throw new AccessDeniedException("Bạn không có quyền xóa thành viên khỏi dự án này!");

        ProjectMemberId id = new ProjectMemberId(projectId,userId);
        projectMemberRepository.deleteById(id);
        
        auditLogService.logAction("REMOVE_MEMBER", EntityType.PROJECT, projectId, "Xóa người dùng ID " + userId + " khỏi dự án");
    }
}
