package com.example.jira.repository;
import com.example.jira.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Integer> {
    List<ProjectVersion> findByProject_ProjectIdOrderByVersionIdDesc(int projectId);
}
