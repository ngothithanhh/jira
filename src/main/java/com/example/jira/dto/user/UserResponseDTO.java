package com.example.jira.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponseDTO {
    private int id;
    private String email;
    private String userName;

}
