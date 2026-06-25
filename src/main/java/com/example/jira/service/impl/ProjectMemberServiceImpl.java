package com.example.jira.service.impl;

import com.example.jira.dto.project.AddMemberRequest;
import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.entity.*;
import com.example.jira.mapper.ProjectMemberMapper;
import com.example.jira.repository.*;
import com.example.jira.service.ProjectMemberService;
import com.example.jira.service.ProjectService;
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

    @Override
    public void addMember(int projectId, AddMemberRequest request){
        //kiem tra vai tro nguoi dung hien tai trong du an co quyen add thanh vien khong
        int userId = SecurityUtils.getCurrentUserId();

        if(!userRoleAssignmentRepository.existsByUser_UserIdAndRole_RoleName(userId,"ADMIN_PROJECT"))
            throw new RuntimeException("Không có quyền thêm thành viên vào dự án!");

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

    }
    @Override
    public List<ProjectMemberResponse> getMembers(int projectId){
        List<ProjectMember> members = projectMemberRepository.findByProject_ProjectId(projectId);

        return members.stream().map(ProjectMemberMapper::toResponse).toList();
    }

    @Override
    public void removeMember(int projectId, int userId){
        ProjectMemberId id = new ProjectMemberId(projectId,userId);
        projectMemberRepository.deleteById(id);
    }
}
