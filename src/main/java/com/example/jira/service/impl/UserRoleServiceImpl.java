package com.example.jira.service.impl;

import com.example.jira.dto.user_role.UserRoleRequest;
import com.example.jira.dto.user_role.UserRoleResponse;
import com.example.jira.entity.UserRole;
import com.example.jira.mapper.UserRoleMapper;
import com.example.jira.repository.ProjectMemberRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.repository.UserRoleRepository;
import com.example.jira.service.UserRoleService;
import com.example.jira.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public List<UserRoleResponse> getAllUserRoles(){
        return userRoleRepository.findAll()
                .stream().map(UserRoleMapper::toResponse).toList();
    }

    @Override
    public UserRoleResponse createRole(UserRoleRequest request){

        if(userRoleRepository.existsByRoleName(request.getRoleName()))
            throw new RuntimeException("Vai trò đã tồn tại!");

        UserRole userRole = new UserRole();
        userRole.setRoleName(request.getRoleName());

        return UserRoleMapper.toResponse(userRoleRepository.save(userRole));
    }

    @Override
    public UserRoleResponse updateRole(int roleId, UserRoleRequest request){
        UserRole userRole = userRoleRepository.findByRoleId(roleId).orElseThrow(()->new RuntimeException("Không tìm thấy vai trò!"));

        userRole.setRoleName(request.getRoleName());

        return UserRoleMapper.toResponse(userRoleRepository.save(userRole));
    }

    @Override
    public void deleteRole(int roleId){
        if (userRoleAssignmentRepository.existsByRole_RoleId(roleId) || projectMemberRepository.existsByRole_RoleId(roleId)) {
            throw new RuntimeException("Không thể xóa vai trò này vì đang được gán cho người dùng hoặc dự án!");
        }
        
        userRoleRepository.deleteById(roleId);
    }
}
