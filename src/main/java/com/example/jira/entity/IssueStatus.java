package com.example.jira.entity;

import com.example.jira.enums.StatusCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "issue_statuses")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class IssueStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id")
    private int statusId;

    @Column(name = "status_name", unique = true, length = 50)
    private String statusName;

    @OneToMany(mappedBy = "fromStatus")
    private Set<IssueTransition> fromIssueTransitions;

    @OneToMany(mappedBy = "toStatus")
    private Set<IssueTransition> toIssueTransitions;

    @OneToMany(mappedBy = "issueStatus")
    private Set<BoardColumnStatus> boardColumnStatuses;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_category", nullable = false)
    private StatusCategory statusCategory;

    @OneToMany(mappedBy = "status")
    private Set<Issue> issues;

}
