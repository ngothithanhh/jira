package com.example.jira.controller;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.ColumnOrderToken;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.MapColumnStatusRequest;
import com.example.jira.dto.board.UpdateColumnOrderRequest;
import com.example.jira.service.BoardService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

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
    public ResponseEntity<Void> updateColumnOrder(@PathVariable String boardId,
            @RequestHeader(value = ColumnOrderToken.HEADER, required = false) String expected,
            @RequestBody JsonNode input) {
        int id;
        try {
            if (!boardId.matches("[1-9][0-9]{0,9}")) throw new NumberFormatException();
            id = Integer.parseInt(boardId);
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID board không hợp lệ");
        }
        boardService.updateColumnOrder(id, expected, UpdateColumnOrderRequest.parse(input));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/columns/map-status")
    public ResponseEntity<Void> unmapStatusFromColumn(@RequestParam int columnId, @RequestParam int statusId) {
        boardService.unmapStatusFromColumn(columnId, statusId);
        return ResponseEntity.noContent().build();
    }
}
