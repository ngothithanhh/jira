package com.example.jira.repository;

import com.example.jira.entity.SprintIssue;
import com.example.jira.entity.SprintIssueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintIssueRepository extends JpaRepository<SprintIssue, SprintIssueId> {
    List<SprintIssue> findBySprint_SprintId(int sprintId);
    void deleteBySprint_SprintIdAndIssue_IssueId(int sprintId, int issueId);
}
