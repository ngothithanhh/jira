package com.example.jira.dto.project;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMemberRequest {
    private int userId;
    private int roleId; 
}
