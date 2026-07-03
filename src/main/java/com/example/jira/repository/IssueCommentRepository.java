package com.example.jira.repository;

import com.example.jira.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment,Integer> {
    List<IssueComment> findByIssue_IssueIdOrderByCreatedAtAsc(int issueId);
}
