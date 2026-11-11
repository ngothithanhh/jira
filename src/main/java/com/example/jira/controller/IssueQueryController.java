package com.example.jira.controller;

import com.example.jira.dto.issue.*;
import com.example.jira.service.impl.IssueQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class IssueQueryController {
    private final IssueQueryService service;
    /** issueId là issues.issue_id; service lấy project sở hữu để kiểm quyền đọc. */
    @GetMapping("/api/issues/{issueId}")
    public IssueResponse detail(@PathVariable int issueId) { return service.detail(issueId); }
    /** Mọi filter đều nằm trong projectId trên URL; query không cho tìm ngoài phạm vi project. */
    @GetMapping("/api/projects/{projectId}/issues/search")
    public IssuePageResponse search(@PathVariable int projectId, @Valid @ModelAttribute IssueSearchRequest request) {
        return service.search(projectId,request);
    }
    /** Version lấy từ lần tải issue; service kiểm trước xóa để không xóa bản vừa bị người khác sửa. */
    @DeleteMapping("/api/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int issueId,@RequestParam(required = false) Long expectedVersion) { service.delete(issueId,expectedVersion); }
}
