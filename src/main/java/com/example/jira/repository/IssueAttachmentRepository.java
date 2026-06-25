package com.example.jira.repository;

import com.example.jira.entity.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment,Integer> {
}
