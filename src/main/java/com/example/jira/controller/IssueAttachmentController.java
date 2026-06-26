package com.example.jira.controller;

import com.example.jira.dto.issue.IssueAttachmentResponse;
import com.example.jira.service.IssueAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueAttachmentController {

    private final IssueAttachmentService attachmentService;

    @PostMapping("/{issueId}/attachments")
    public ResponseEntity<IssueAttachmentResponse> uploadAttachment(
            @PathVariable int issueId,
            @RequestParam("file") MultipartFile file) {
        return new ResponseEntity<>(attachmentService.uploadAttachment(issueId, file), HttpStatus.CREATED);
    }

    @GetMapping("/{issueId}/attachments")
    public ResponseEntity<List<IssueAttachmentResponse>> getAttachments(@PathVariable int issueId) {
        return ResponseEntity.ok(attachmentService.getAttachments(issueId));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable int attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok().build();
    }
}
