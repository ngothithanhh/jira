package com.example.jira.controller;

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

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(@PathVariable int issueId, @Valid @RequestBody CreateCommentRequest request) {
        return new ResponseEntity<>(issueCommentService.addComment(issueId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable int issueId) {
        return ResponseEntity.ok(issueCommentService.getCommentsByIssue(issueId));
    }
}
