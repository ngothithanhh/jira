package com.example.jira.service.impl;

import com.example.jira.config.PasswordConfig;
import com.example.jira.dto.user.UpdateUserRequest;
import com.example.jira.dto.user.UserResponse;
import com.example.jira.entity.User;
import com.example.jira.enums.PermissionName;
import com.example.jira.mapper.UserMapper;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.GlobalSecurity;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.service.UserService;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordConfig passwordConfig;
    private final GlobalSecurity globalSecurity;
    @Override
    public UserResponse getUserById(int userId){
        User user = userRepository.findById(userId).orElseThrow(()->new RuntimeException("Không tìm thấy người dùng!"));

        return UserMapper.toResponseFull(user);

    }
    @Override
    public List<UserResponse> getAllUsers(){
        return userRepository.findAll()
                .stream()
                .map(UserMapper::toResponseFull)
                .toList();

    }

//    @Override
//    public UserResponse updateUser(int userId, UpdateUserRequest request){
//
//
//    }
    @Override
    public void deleteUser(int userId){
        int currentUserId = SecurityUtils.getCurrentUserId();
        if(!globalSecurity.hasPermission(PermissionName.DELETE_USER)){
            throw new RuntimeException("Bạn không có quyền xóa người dùng này!");
        }

        userRepository.deleteById(userId);

    }
}
