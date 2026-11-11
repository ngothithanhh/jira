package com.example.jira.repository;
import com.example.jira.entity.SprintMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SprintMetricRepository extends JpaRepository<SprintMetric,Long> {
    Optional<SprintMetric> findBySprint_SprintId(int sprintId);
    List<SprintMetric> findBySprint_SprintIdIn(Collection<Integer> sprintIds);
}
