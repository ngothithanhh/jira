package com.example.jira.service;

import com.example.jira.dto.board.*;

import java.util.List;

public interface BoardService {
    BoardResponse updateBoard(int boardId, UpdateBoardRequest request);
    void deleteBoard(int boardId);
    BoardResponse updateColumn(int columnId, UpdateColumnRequest request);
    void deleteColumn(int columnId);
    BoardResponse moveIssue(int boardId, int issueId, MoveBoardIssueRequest request, boolean adding);
    void removeIssue(int boardId, int issueId);
    BoardResponse createBoard(CreateBoardRequest request);
    BoardResponse getBoard(int boardId);
    List<BoardResponse> getBoardsByProject(int projectId);
    BoardResponse addColumn(CreateColumnRequest request);
    void mapStatusToColumn(int columnId, int statusId);
    void updateColumnOrder(int boardId, List<UpdateColumnOrderRequest> requests);
    void unmapStatusFromColumn(int columnId, int statusId);
}
