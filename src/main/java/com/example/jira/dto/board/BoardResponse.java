package com.example.jira.dto.board;

import com.example.jira.dto.issue.IssueResponse;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BoardResponse {
    private int boardId;
    private int projectId;
    private String boardName;
    private String boardType;
    private LocalDateTime createdAt;
    private List<BoardColumnDto> columns;

    @Data
    public static class BoardIssueDto {
        private int position;
        private IssueResponse issue;
    }
    @Data
    public static class BoardColumnDto {
        private int columnId;
        private String columnName;
        private int position;
        private int wipLimit;
        private List<Integer> mappedStatusIds;
        private List<BoardIssueDto> issues = new ArrayList<>();
    }
}
