package com.example.jira.dto.board;

import lombok.Data;

import java.time.LocalDateTime;
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
    public static class BoardColumnDto {
        private int columnId;
        private String columnName;
        private int position;
        private int wipLimit;
        private List<Integer> mappedStatusIds;
    }
}
