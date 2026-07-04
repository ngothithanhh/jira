package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workflow_rules")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WorkflowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id", nullable = false)
    private IssueStatus fromStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", nullable = false)
    private IssueStatus toStatus;

}
