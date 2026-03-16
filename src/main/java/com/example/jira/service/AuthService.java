package com.example.jira.service;

import com.example.jira.dto.auth.AuthResponseDTO;
import com.example.jira.dto.auth.LoginRequestDTO;
import com.example.jira.dto.auth.RegisterRequestDTO;
import com.example.jira.dto.user.UserResponseDTO;

public interface AuthService {
    AuthResponseDTO login(LoginRequestDTO request);
    AuthResponseDTO register(RegisterRequestDTO request);
    AuthResponseDTO refreshToken(String refreshToken);
    void logout(String refreshToken);
    UserResponseDTO getCurrentUser(String email);


}
