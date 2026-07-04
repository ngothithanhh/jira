package com.example.jira.dto.user_role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRoleResponse {
    private int role;
    private String roleName;
}
