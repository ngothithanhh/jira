package com.example.jira.repository;

import com.example.jira.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueCommentRepository extends JpaRepository<IssueComment,Integer> {
}
