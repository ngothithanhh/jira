package com.example.jira.dto.issue.issue_status;

import com.example.jira.enums.StatusCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IssueStatusRequest {
    @NotBlank(message = "Tên trạng thái không được để trống")
    private String statusName;

    @NotNull(message = "Category không được để trống")
    private StatusCategory statusCategory;
}
