package com.example.jira.dto.board;

import com.example.jira.dto.issue.IssueResponse;

public record BoardCardResponse(IssueResponse issue, Integer columnId, int position, Long version) { }
