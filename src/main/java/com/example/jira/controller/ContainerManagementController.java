package com.example.jira.controller;

import com.example.jira.dto.board.*;
import com.example.jira.dto.sprint.*;
import com.example.jira.service.impl.ContainerManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ContainerManagementController {
    private final ContainerManagementService service;
    /** ID sprints trên URL xác định project cần MANAGE_SPRINT; chỉ trường khác null được sửa. */
    @PatchMapping("/api/sprints/{sprintId}")
    public SprintResponse sprint(@PathVariable int sprintId,@Valid @RequestBody UpdateSprintRequest body) { return service.updateSprint(sprintId,body); }
    /** Chỉ xóa FUTURE, không xóa lịch sử sprint đã chạy. */
    @DeleteMapping("/api/sprints/{sprintId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSprint(@PathVariable int sprintId) { service.deleteSprint(sprintId); }
    /** Đổi tên board dưới quyền MANAGE_BOARD, không chuyển board sang project khác. */
    @PatchMapping("/api/boards/{boardId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void board(@PathVariable int boardId,@Valid @RequestBody UpdateBoardRequest body) { service.updateBoard(boardId,body); }
    /** Xóa cấu hình/rank của board; giữ nguyên issue và project. */
    @DeleteMapping("/api/boards/{boardId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBoard(@PathVariable int boardId) { service.deleteBoard(boardId); }
    /** columnId là board_columns, không phải vị trí cột; service kiểm WIP trước ghi. */
    @PatchMapping("/api/boards/columns/{columnId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void column(@PathVariable int columnId,@Valid @RequestBody UpdateColumnRequest body) { service.updateColumn(columnId,body); }
    /** Cột còn issue theo mapping thì trả 409, cần chuyển issue trước. */
    @DeleteMapping("/api/boards/columns/{columnId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable int columnId) { service.deleteColumn(columnId); }
}
