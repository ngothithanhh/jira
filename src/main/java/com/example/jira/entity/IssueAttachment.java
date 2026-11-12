package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "issue_attachments")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class IssueAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private int attachmentId;

    @ManyToOne
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @Column(name = "file_path")
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_user_id")
    private User user;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    // Metadata mới để F5 vẫn giữ tên gốc; null biểu thị tệp legacy chưa có thông tin này.
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name = "content_type", length = 255)
    private String contentType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "cloud_public_id", length = 1024)
    private String cloudPublicId;
    @Column(name = "cloud_resource_type", length = 10)
    private String cloudResourceType;
    @Column(name="storage_type",length=16)
    private String storageType;

}
