package com.example.jira.mapper;

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
}
