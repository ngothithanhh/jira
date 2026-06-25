package com.example.jira.repository;

import com.example.jira.entity.IssueTransition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueTransitionRepository extends JpaRepository<IssueTransition, Integer> {
}
