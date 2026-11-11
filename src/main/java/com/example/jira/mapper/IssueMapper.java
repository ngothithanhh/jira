package com.example.jira.mapper;

import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.entity.Issue;

public class IssueMapper {

    /**
     * Dùng sau khi đọc/lưu issue để tạo response, không ghi thêm DB.
     * Issue chứa ID project, user báo cáo, user nhận việc và issue cha từ các quan hệ tương ứng.
     * Nếu đầu vào null thì trả null; nếu thiếu reporter/assignee/parent thì giữ ID và tên null.
     * Các ID tùy chọn trong IssueResponse dùng Integer để issue chưa giao hoặc không có cha vẫn trả được JSON.
     */
    public static IssueResponse toResponse(Issue issue) {
        // Bên gọi không có issue thì không tạo response rỗng chứa các ID giả.
        if (issue == null) {
            return null;
        }

        // Chỉ lấy ID của quan hệ đang có; giữ nguyên cách map project, trạng thái và loại công việc.
        return IssueResponse.builder()
                .issueId(issue.getIssueId())
                .version(issue.getVersion())
                .issueKey(issue.getIssueKey())
                .summary(issue.getSummary())
                .description(issue.getDescription())
                .priority(issue.getPriority())
                .storyPoints(issue.getStoryPoints())
                
                .statusName(issue.getStatus() != null ? issue.getStatus().getStatusName() : null)
                .issueTypeName(issue.getIssueType() != null ? issue.getIssueType().getTypeName() : null)

                .projectId(issue.getProject() != null ? issue.getProject().getProjectId() : 0)
                .projectKey(issue.getProject() != null ? issue.getProject().getProjectKey() : null)

                .reporterId(issue.getReporter() != null ? issue.getReporter().getUserId() : null)
                .reporterName(issue.getReporter() != null ? issue.getReporter().getUserName() : null)
                
                .assigneeId(issue.getAssignee() != null ? issue.getAssignee().getUserId() : null)
                .assigneeName(issue.getAssignee() != null ? issue.getAssignee().getUserName() : null)

                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())

                .parentIssueId(issue.getParentIssue() != null ? issue.getParentIssue().getIssueId() : null)
                .build();
    }
}
