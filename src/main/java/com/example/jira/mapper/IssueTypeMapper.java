package com.example.jira.mapper;

import com.example.jira.dto.issue.issue_type.IssueTypeResponse;
import com.example.jira.entity.IssueType;

public class IssueTypeMapper {
    public static IssueTypeResponse toResponse(IssueType type){
        return IssueTypeResponse.builder()
                .issueTypeId(type.getIssueTypeId())
                .typeName(type.getTypeName())
                .build();
    }
}
