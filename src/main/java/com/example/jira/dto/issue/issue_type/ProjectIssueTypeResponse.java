package com.example.jira.dto.issue.issue_type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectIssueTypeResponse {
    private int projectId;
    private int issueTypeId;
    private String issueTypeName;
    private boolean isDefault;

    /** FE dùng isDefault. Giữ cả tên default của getter hiện có cho các client cũ. */
    @JsonProperty("isDefault")
    public boolean configuredDefault() {
        return isDefault;
    }
}
