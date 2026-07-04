package com.example.jira.repository;

import com.example.jira.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, Integer> {
    boolean existsByFromStatus_StatusIdAndToStatus_StatusId(int fromStatusId, int toStatusId);
    List<WorkflowRule> findByFromStatus_StatusId(int fromStatusId);
}
