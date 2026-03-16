package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "issues")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Issue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private int issueId;

    @Column(nullable = false)
    private String summary;

    private String description;

    private String priority;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Column(name = "story_points")
    private int storyPoints;

    @Column(nullable = false, name = "issue_key")
    private String issueKey;

    @Column(nullable = false, name = "issue_number")
    private int issueNumber;

    @OneToMany(mappedBy = "issue")
    private Set<BoardIssue> boardIssues;

    @OneToMany(mappedBy = "issue")
    private Set<WorkLog> workLogs;

    @OneToMany(mappedBy = "issue")
    private Set<IssueTransition> issueTransitions;

    @OneToMany(mappedBy = "issue")
    private Set<IssueComment> issueComments;

    @ManyToOne
    @JoinColumn(name = "issue_type_id")
    private IssueType issueType;

    @OneToMany(mappedBy = "issue")
    private Set<IssueAttachment> issueAttachments;

    @OneToMany(mappedBy = "issue")
    private Set<SprintIssue> sprintIssues;

    @OneToMany(mappedBy = "issue")
    private Set<IssueVersion> issueVersions;

    @ManyToOne
    @JoinColumn(name = "parent_issue_id")
    private Issue parentIssue;

    @OneToMany(mappedBy = "parentIssue")
    private List<Issue> subTasks;
}
