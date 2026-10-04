package com.example.jira.repository;

import com.example.jira.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, Integer> {
    boolean existsByProject_ProjectIdAndFromStatus_StatusIdAndToStatus_StatusId(int projectId, int fromStatusId, int toStatusId);
    List<WorkflowRule> findByProject_ProjectId(int projectId);
    List<WorkflowRule> findByProject_ProjectIdAndFromStatus_StatusId(int projectId, int fromStatusId);
}
