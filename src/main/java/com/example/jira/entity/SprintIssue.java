package com.example.jira.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sprint_issues")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class SprintIssue {
    @EmbeddedId
    private SprintIssueId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sprintId")
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("issueId")
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

}
