package com.example.jira.repository;

import com.example.jira.entity.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueStatusRepository extends JpaRepository<IssueStatus, Integer> {
    boolean existsByStatusName(String statusName);
}
