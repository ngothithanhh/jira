package com.example.jira.dto.issue;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IssueTransitionResponse {
    private int transitionId;
    private int fromStatusId;
    private String fromStatusName;
    private int toStatusId;
    private String toStatusName;
    private Integer userId;
    private String userName;
    private LocalDateTime transitionedAt;
}
