package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_issue_types")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProjectIssueType {

    @EmbeddedId
    private ProjectIssueTypeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("issueTypeId")
    @JoinColumn(name = "issue_type_id")
    private IssueType issueType;

    @Column(name = "is_default")
    private boolean isDefault = false;
}
