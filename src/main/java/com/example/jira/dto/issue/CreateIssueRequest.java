package com.example.jira.dto.issue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private LocalDate dueDate;
    private Long backlogRank;
    private Integer boardPosition;
    
    private Integer parentIssueId;
}
