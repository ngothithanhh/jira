package com.example.jira.service;

import com.example.jira.dto.user.UpdateUserRequest;
import com.example.jira.dto.user.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse getUserById(int userId);
    List<UserResponse> getAllUsers();
//    UserResponse updateUser(int userId, UpdateUserRequest request);
    void deleteUser(int userId);

}
