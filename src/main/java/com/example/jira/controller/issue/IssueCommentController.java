package com.example.jira.controller.issue;

import com.example.jira.dto.issue.CommentResponse;
import com.example.jira.dto.issue.CreateCommentRequest;
import com.example.jira.service.IssueCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/issues/{issueId}/comments")
@RequiredArgsConstructor
public class IssueCommentController {

    private final IssueCommentService issueCommentService;
    /** expectedVersion từ comment đã tải; service kiểm tác giả/quyền và scope issue trước ghi. */
    @PutMapping("/{commentId}")
    public CommentResponse updateComment(@PathVariable int issueId,@PathVariable int commentId,@Valid @RequestBody com.example.jira.dto.issue.UpdateCommentRequest body) {
        return issueCommentService.updateComment(issueId,commentId,body);
    }
    /** Xóa đúng comment của issue trên URL, version cũ trả 409. */
    @DeleteMapping("/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable int issueId,@PathVariable int commentId,@RequestParam(required=false) Long expectedVersion) {
        issueCommentService.deleteComment(issueId,commentId,expectedVersion);
    }

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(@PathVariable int issueId, @Valid @RequestBody CreateCommentRequest request) {
        return new ResponseEntity<>(issueCommentService.addComment(issueId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable int issueId) {
        return ResponseEntity.ok(issueCommentService.getCommentsByIssue(issueId));
    }
}
