package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ tải bảng này sau kiểm quyền download; danh sách metadata không có quan hệ eager đến nội dung. */
@Entity @Table(name="issue_attachment_contents") @Getter @Setter @NoArgsConstructor
public class IssueAttachmentContent {
    @Id @Column(name="attachment_id") private Integer attachmentId;
    @MapsId @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="attachment_id")
    private IssueAttachment attachment;
    @Column(name="content",nullable=false,columnDefinition="bytea") private byte[] content;
}
