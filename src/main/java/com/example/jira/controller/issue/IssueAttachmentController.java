package com.example.jira.controller.issue;

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
            @RequestParam("file") MultipartFile file,
            org.springframework.web.multipart.MultipartHttpServletRequest multipart) {
        // API chỉ nhận một file; ID issue/user/URL từ part thừa không được đổi đối tượng lưu.
        if (multipart.getMultiFileMap().size()!=1 || multipart.getFiles("file").size()!=1 || !multipart.getParameterMap().isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ nhận một trường file");
        return new ResponseEntity<>(attachmentService.uploadAttachment(issueId, file), HttpStatus.CREATED);
    }

    @GetMapping("/{issueId}/attachments")
    public ResponseEntity<List<IssueAttachmentResponse>> getAttachments(@PathVariable int issueId) {
        return ResponseEntity.ok(attachmentService.getAttachments(issueId));
    }

    /** Nội dung chỉ đọc sau kiểm VIEW_PROJECT và parent; trả attachment/nosniff/no-store, không redirect tới URL bên ngoài. */
    @GetMapping("/{issueId}/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable int issueId,@PathVariable int attachmentId) {
        var file=attachmentService.download(issueId,attachmentId);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
            .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(file.fileName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff")
            .header("Cache-Control","no-store, private")
            .body(file.content());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable int attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok().build();
    }

    /** issueId và attachmentId phải cùng parent trong DB; service xóa metadata/bytea cùng transaction sau kiểm quyền. */
    @DeleteMapping("/{issueId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteFromIssue(@PathVariable int issueId,@PathVariable int attachmentId) {
        attachmentService.deleteAttachmentForIssue(issueId,attachmentId);
        return ResponseEntity.noContent().build();
    }
}
