package com.example.jira.dto.issue;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class IssueSearchRequest {
    @Size(max = 200) private String keyword;
    @Positive private Integer statusId;
    @Positive private Integer typeId;
    @Positive private Integer assigneeId;
    @Positive private Integer reporterId;
    @Positive private Integer sprintId;
    @Size(max = 50) private String priority;
    private boolean unassigned;
    @Min(0) private int page = 0;
    @Min(1) @Max(100) private int size = 20;
    private String sort = "createdAt";
    private String direction = "DESC";
}
