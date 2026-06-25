package com.example.jira.repository;

import com.example.jira.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project,Integer> {
    boolean existsByProjectKey(String projectKey);
}
