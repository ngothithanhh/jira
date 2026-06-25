package com.example.jira.dto.user;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSummary {
    private int id;
    private String email;
    private String userName;

}
