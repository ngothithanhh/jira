package com.example.jira.dto.account;

import com.example.jira.dto.user.UserResponse;
import java.util.List;

/** Quyền của chính phiên hiện tại; projectId vắng nghĩa là chưa yêu cầu phạm vi dự án. */
public record AccountContext(UserResponse user, boolean systemAdmin, List<String> globalPermissions,
                             Integer projectId, List<String> projectPermissions) {}
