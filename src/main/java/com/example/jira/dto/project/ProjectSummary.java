package com.example.jira.dto.project;

import com.example.jira.dto.user.UserSummary;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectSummary {
    private int projectId;
    private String projectName;
    private String projectKey;
    private String description;
    private UserSummary leader;

    // Số liệu theo dữ liệu thật của đúng project; không lấy từ fixture FE hoặc đếm riêng từng dòng.
    private long issueCount;
    private long memberCount;
    private long sprintCount;
    private String activeSprintName;

}
