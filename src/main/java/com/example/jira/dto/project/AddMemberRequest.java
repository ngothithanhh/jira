package com.example.jira.dto.project;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMemberRequest {
    @Positive
    private int userId;
    @Positive
    private int roleId;
}
