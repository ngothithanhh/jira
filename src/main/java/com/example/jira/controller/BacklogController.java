package com.example.jira.controller;

import com.example.jira.dto.backlog.BacklogOrderRequest;
import com.example.jira.dto.issue.IssueResponse;
import com.example.jira.service.impl.BacklogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BacklogController {
    private final BacklogService service;

    /** projectId là projects.project_id trên URL; service kiểm VIEW_PROJECT rồi trả thứ tự hiện tại. */
    @GetMapping("/api/projects/{projectId}/backlog")
    public List<IssueResponse> product(@PathVariable int projectId) { return service.product(projectId); }

    /** Body chứa toàn bộ ID issues; service kiểm scope dưới khóa trước khi lưu thứ tự. */
    @PutMapping("/api/projects/{projectId}/backlog/order")
    public List<IssueResponse> orderProduct(@PathVariable int projectId, @Valid @RequestBody BacklogOrderRequest body) {
        return service.orderProduct(projectId, body.getIssueIds());
    }

    /** sprintId xác định project cần quyền đọc; không cho client chọn project khác để mượn quyền. */
    @GetMapping("/api/sprints/{sprintId}/issues")
    public List<IssueResponse> sprint(@PathVariable int sprintId) { return service.sprint(sprintId); }

    /** Chỉ MANAGE_SPRINT trên project sở hữu được sắp xếp sprint chưa CLOSED. */
    @PutMapping("/api/sprints/{sprintId}/issues/order")
    public List<IssueResponse> orderSprint(@PathVariable int sprintId, @Valid @RequestBody BacklogOrderRequest body) {
        return service.orderSprint(sprintId, body.getIssueIds());
    }
}
