package com.example.jira.mapper;

import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.entity.Issue;

public class IssueMapper {

    public static IssueResponse toResponse(Issue issue) {
        if (issue == null) {
            return null;
        }

        return IssueResponse.builder()
                .issueId(issue.getIssueId())
                .issueKey(issue.getIssueKey())
                .summary(issue.getSummary())
                .description(issue.getDescription())
                .priority(issue.getPriority())
                .storyPoints(issue.getStoryPoints())
                
                .statusName(issue.getStatus() != null ? issue.getStatus().getStatusName() : null)
                .issueTypeName(issue.getIssueType() != null ? issue.getIssueType().getTypeName() : null)

                .projectId(issue.getProject() != null ? issue.getProject().getProjectId() : 0)
                .projectKey(issue.getProject() != null ? issue.getProject().getProjectKey() : null)

                .reporterId(issue.getReporter() != null ? issue.getReporter().getUserId() : null)
                .reporterName(issue.getReporter() != null ? issue.getReporter().getUserName() : null)
                
                .assigneeId(issue.getAssignee() != null ? issue.getAssignee().getUserId() : null)
                .assigneeName(issue.getAssignee() != null ? issue.getAssignee().getUserName() : null)

                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())

                .parentIssueId(issue.getParentIssue() != null ? issue.getParentIssue().getIssueId() : null)
                .build();
    }
}
