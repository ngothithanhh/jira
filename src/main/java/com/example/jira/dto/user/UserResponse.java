package com.example.jira.dto.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private int userId;
    private String userName;
    private String email;
    private String fullName;
    private LocalDateTime createdAt;
}
