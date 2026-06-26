package com.example.jira.repository;

import com.example.jira.entity.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment,Integer> {
    List<IssueAttachment> findByIssue_IssueIdOrderByUploadedAtAsc(int issueId);
}
