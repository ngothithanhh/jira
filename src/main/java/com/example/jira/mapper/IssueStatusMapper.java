package com.example.jira.mapper;

import com.example.jira.dto.issue.issue_status.IssueStatusResponse;
import com.example.jira.entity.IssueStatus;

public class IssueStatusMapper {
    public static IssueStatusResponse toResponse(IssueStatus status){
        return IssueStatusResponse.builder()
                .statusId(status.getStatusId())
                .statusName(status.getStatusName())
                .statusCategory(status.getStatusCategory())
                .build();
    }
}
