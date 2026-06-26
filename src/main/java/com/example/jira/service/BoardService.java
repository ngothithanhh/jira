package com.example.jira.service;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;

import java.util.List;

public interface BoardService {
    BoardResponse createBoard(CreateBoardRequest request);
    BoardResponse getBoard(int boardId);
    List<BoardResponse> getBoardsByProject(int projectId);
    BoardResponse addColumn(CreateColumnRequest request);
    void mapStatusToColumn(int columnId, int statusId);
}
