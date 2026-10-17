package com.example.jira.dto.issue;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RankBacklogIssueRequest {
    @NotNull
    private Integer issueId;
    private Integer beforeIssueId;
}
