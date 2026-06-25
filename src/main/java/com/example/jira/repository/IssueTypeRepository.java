package com.example.jira.repository;

import com.example.jira.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueTypeRepository extends JpaRepository<IssueType, Integer> {
}
