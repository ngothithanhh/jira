package com.example.jira.dto.issue.issue_type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueTypeResponse {
    // ID issue_types để client dùng tiếp ở API gán loại vào project/tạo issue.
    private int issueTypeId;
    private String typeName;
}
