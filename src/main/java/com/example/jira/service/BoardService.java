package com.example.jira.service;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.UpdateColumnOrderRequest;

import java.util.List;

public interface BoardService {
    BoardResponse createBoard(CreateBoardRequest request);
    BoardResponse getBoard(int boardId);
    List<BoardResponse> getBoardsByProject(int projectId);
    BoardResponse addColumn(CreateColumnRequest request);
    void mapStatusToColumn(int columnId, int statusId);
    void updateColumnOrder(int boardId, List<UpdateColumnOrderRequest> requests);
    void unmapStatusFromColumn(int columnId, int statusId);
}
