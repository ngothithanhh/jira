package com.example.jira.repository;

import com.example.jira.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueCommentRepository extends JpaRepository<IssueComment,Integer> {
    List<IssueComment> findByIssue_IssueIdOrderByCreatedAtAsc(int issueId);
}
