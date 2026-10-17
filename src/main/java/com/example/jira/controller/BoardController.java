package com.example.jira.controller;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.MapColumnStatusRequest;
import com.example.jira.dto.board.UpdateColumnOrderRequest;
import com.example.jira.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @PutMapping("/{boardId}")
    public BoardResponse updateBoard(@PathVariable int boardId, @Valid @RequestBody com.example.jira.dto.board.UpdateBoardRequest request) {
        return boardService.updateBoard(boardId, request);
    }
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> deleteBoard(@PathVariable int boardId) {
        boardService.deleteBoard(boardId); return ResponseEntity.noContent().build();
    }
    @PutMapping("/columns/{columnId}")
    public BoardResponse updateColumn(@PathVariable int columnId, @Valid @RequestBody com.example.jira.dto.board.UpdateColumnRequest request) {
        return boardService.updateColumn(columnId, request);
    }
    @DeleteMapping("/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(@PathVariable int columnId) {
        boardService.deleteColumn(columnId); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{boardId}/issues/{issueId}")
    public BoardResponse addIssue(@PathVariable int boardId, @PathVariable int issueId, @Valid @RequestBody com.example.jira.dto.board.MoveBoardIssueRequest request) {
        return boardService.moveIssue(boardId, issueId, request, true);
    }
    @PutMapping("/{boardId}/issues/{issueId}/move")
    public BoardResponse moveIssue(@PathVariable int boardId, @PathVariable int issueId, @Valid @RequestBody com.example.jira.dto.board.MoveBoardIssueRequest request) {
        return boardService.moveIssue(boardId, issueId, request, false);
    }
    @DeleteMapping("/{boardId}/issues/{issueId}")
    public ResponseEntity<Void> removeIssue(@PathVariable int boardId, @PathVariable int issueId) {
        boardService.removeIssue(boardId, issueId); return ResponseEntity.noContent().build();
    }
    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(@Valid @RequestBody CreateBoardRequest request) {
        return new ResponseEntity<>(boardService.createBoard(request), HttpStatus.CREATED);
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<BoardResponse> getBoard(@PathVariable int boardId) {
        return ResponseEntity.ok(boardService.getBoard(boardId));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<BoardResponse>> getBoardsByProject(@PathVariable int projectId) {
        return ResponseEntity.ok(boardService.getBoardsByProject(projectId));
    }

    @PostMapping("/columns")
    public ResponseEntity<BoardResponse> addColumn(@Valid @RequestBody CreateColumnRequest request) {
        return new ResponseEntity<>(boardService.addColumn(request), HttpStatus.CREATED);
    }

    @PostMapping("/columns/map-status")
    public ResponseEntity<Void> mapStatusToColumn(@Valid @RequestBody MapColumnStatusRequest request) {
        boardService.mapStatusToColumn(request.getColumnId(), request.getStatusId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{boardId}/columns/order")
    public ResponseEntity<Void> updateColumnOrder(@PathVariable int boardId, @Valid @RequestBody List<UpdateColumnOrderRequest> requests) {
        boardService.updateColumnOrder(boardId, requests);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/columns/map-status")
    public ResponseEntity<Void> unmapStatusFromColumn(@RequestParam int columnId, @RequestParam int statusId) {
        boardService.unmapStatusFromColumn(columnId, statusId);
        return ResponseEntity.noContent().build();
    }
}
