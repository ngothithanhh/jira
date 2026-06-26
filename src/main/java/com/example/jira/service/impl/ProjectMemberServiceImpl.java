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
import com.example.jira.service.AuditLogService;
import com.example.jira.service.NotificationService;
import com.example.jira.ultils.SecurityUtils;
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

    @Override
    public void addMember(int projectId, AddMemberRequest request){

        if(!projectSecurity.hasPermission(projectId,"ADD_MEMBER"))
            throw new RuntimeException("Bạn không có quyền thêm thành viên!");

        Project project = projectRepository.findById(projectId).orElseThrow(()->new RuntimeException("Không tìm thấy dự án!"));

        User user = userRepository.findById(request.getUserId()).orElseThrow(()->new RuntimeException("Không tìm thấy người dùng!"));

        UserRole userRole = userRoleRepositoty.findByRoleId(request.getRoleId()).orElseThrow(()->new RuntimeException("Không tìm thấy vai trò!"));

        ProjectMemberId id = new ProjectMemberId(projectId,user.getUserId());

        if(projectMemberRepository.existsById(id)){
            throw new RuntimeException("Người dùng đã có trong dự án!");
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
    @Override
    public List<ProjectMemberResponse> getMembers(int projectId){
        List<ProjectMember> members = projectMemberRepository.findByProject_ProjectId(projectId);

        return members.stream().map(ProjectMemberMapper::toResponse).toList();
    }

    @Override
    public void removeMember(int projectId, int userId){

        if(!projectSecurity.hasPermission(projectId,"DELETE_MEMBER"))
            throw new RuntimeException("Bạn không có quyền xóa thành viên!");

        ProjectMemberId id = new ProjectMemberId(projectId,userId);
        projectMemberRepository.deleteById(id);
        
        auditLogService.logAction("REMOVE_MEMBER", EntityType.PROJECT, projectId, "Xóa người dùng ID " + userId + " khỏi dự án");
    }
}
