package com.example.jira.controller;

import com.example.jira.dto.board.BoardResponse;
import com.example.jira.dto.board.CreateBoardRequest;
import com.example.jira.dto.board.CreateColumnRequest;
import com.example.jira.dto.board.MapColumnStatusRequest;
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
}
