package com.example.jira.mapper;

import com.example.jira.dto.user.UserResponse;
import com.example.jira.dto.user.UserSummary;
import com.example.jira.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public static UserSummary toResponse(User user){
        return UserSummary.builder()
                .id(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .build();
    }

    public static UserResponse toResponseFull(User user){
        return UserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .userName(user.getUserName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
