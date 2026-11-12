package com.example.jira.repository;
import com.example.jira.entity.IssueAttachmentContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueAttachmentContentRepository extends JpaRepository<IssueAttachmentContent,Integer> {
    /** Xóa theo khóa, không SELECT bytea lên RAM chỉ để xóa một tệp. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from IssueAttachmentContent c where c.attachmentId=:id")
    int deleteForAttachment(@org.springframework.data.repository.query.Param("id") int id);
}
