package com.example.jira.mapper;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.entity.Board;
import com.example.jira.entity.BoardColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BoardMapper {
    public static BoardResponse toResponse(Board board, List<BoardColumn> columns) {
        if (board == null) return null;
        
        BoardResponse dto = new BoardResponse();
        dto.setBoardId(board.getBoardId());
        if (board.getProject() != null) {
            dto.setProjectId(board.getProject().getProjectId());
        }
        dto.setBoardName(board.getBoardName());
        dto.setBoardType(board.getBoardType());
        dto.setCreatedAt(board.getCreatedAt());

        List<BoardResponse.BoardColumnDto> colDtos = new ArrayList<>();
        if (columns != null) {
            for (BoardColumn col : columns) {
                BoardResponse.BoardColumnDto colDto = new BoardResponse.BoardColumnDto();
                colDto.setColumnId(col.getColumnId());
                colDto.setColumnName(col.getColumnName());
                colDto.setPosition(col.getPosition());
                colDto.setWipLimit(col.getWipLimit());

                List<Integer> statusIds = new ArrayList<>();
                if (col.getBoardColumnStatuses() != null) {
                    statusIds = col.getBoardColumnStatuses().stream()
                            .map(bcs -> bcs.getIssueStatus().getStatusId())
                            .collect(Collectors.toList());
                }
                colDto.setMappedStatusIds(statusIds);
                colDtos.add(colDto);
            }
        }
        dto.setColumns(colDtos);
        return dto;
    }
}
