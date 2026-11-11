package com.example.jira.controller;

import com.example.jira.dto.board.*;
import com.example.jira.service.impl.BoardCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardId}/issues")
public class BoardCardController {
    private final BoardCardService service;
    /** boardId xác định project kiểm quyền đọc; service suy ra cột từ trạng thái hiện tại, không ghi DB. */
    @GetMapping public List<BoardCardResponse> cards(@PathVariable int boardId) { return service.cards(boardId); }
    /** Gửi version của card đã tải để service chặn hai lượt cùng kéo từ một bản cũ. */
    @PutMapping("/{issueId}/move")
    public BoardCardResponse move(@PathVariable int boardId, @PathVariable int issueId, @Valid @RequestBody MoveBoardIssueRequest body) {
        return service.move(boardId,issueId,body);
    }
}
