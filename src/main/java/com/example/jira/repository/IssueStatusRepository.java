package com.example.jira.repository;

import com.example.jira.entity.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jira.enums.StatusCategory;

import java.util.Optional;

@Repository
public interface IssueStatusRepository extends JpaRepository<IssueStatus, Integer> {
    boolean existsByStatusName(String statusName);
    Optional<IssueStatus> findFirstByStatusCategoryOrderByStatusIdAsc(StatusCategory statusCategory);
}
