package com.example.jira.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {
    private String email;
    private String fullName;
    private String userName;
    private String password;


}
