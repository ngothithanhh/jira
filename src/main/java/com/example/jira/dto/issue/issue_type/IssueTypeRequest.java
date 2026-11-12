package com.example.jira.dto.issue.issue_type;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class IssueTypeRequest {
    @NotBlank
    @Size(max = 50)
    private String typeName;
}
