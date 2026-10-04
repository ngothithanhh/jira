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
public class UpdateIssueRequest {
    private String summary;
    private String description;
    private String priority;
    private Integer storyPoints;
    private LocalDate dueDate;
    private Long backlogRank;
    private Integer boardPosition;
}
