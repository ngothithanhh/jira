package com.example.jira.dto.issue;

import java.util.List;

public record IssuePageResponse(List<IssueResponse> items, int page, int size, long totalItems, int totalPages) { }
