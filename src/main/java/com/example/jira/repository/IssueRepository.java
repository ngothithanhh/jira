package com.example.jira.repository;

import com.example.jira.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue,Integer> {
    
    @Query("SELECT MAX(i.issueNumber) FROM Issue i WHERE i.project.projectId = :projectId")
    Optional<Integer> findMaxIssueNumberByProjectId(@Param("projectId") int projectId);
}
