package com.example.jira.service;

import com.example.jira.entity.Issue;
import com.example.jira.enums.PermissionName;
import com.example.jira.repository.IssueRepository;
import com.example.jira.security.ProjectSecurity;
import com.example.jira.ultils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class IssueCollaborationAccess {
    private final IssueRepository issues;
    private final ProjectSecurity security;

    public Issue requireView(int issueId) {
        Issue issue = issues.findByIssueIdAndDeletedAtIsNull(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc"));
        if (!security.hasPermission(issue.getProject().getProjectId(), PermissionName.VIEW_PROJECT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập dự án này");
        return issue;
    }

    public void requireOwnerOrEditor(Issue issue, int ownerId) {
        if (ownerId != SecurityUtils.getCurrentUserId()
                && !security.hasPermission(issue.getProject().getProjectId(), PermissionName.EDIT_ISSUE))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền chỉnh sửa nội dung này");
    }
}
