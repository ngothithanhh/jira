package com.example.jira.dto.issue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {
    private int projectId;
    private int issueTypeId;
    private String summary;
    private String description;
    private String priority;
    
    private Integer parentIssueId;
}
