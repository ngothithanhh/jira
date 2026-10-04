package com.example.jira.repository;

import com.example.jira.entity.IssueTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTransitionRepository extends JpaRepository<IssueTransition, Integer> {
    List<IssueTransition> findByIssue_IssueIdOrderByTransitionedAtDesc(int issueId);
}
