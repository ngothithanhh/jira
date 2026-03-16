package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "issue_transitions")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class IssueTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int transition_id;

    @ManyToOne
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne
    @JoinColumn(name = "from_status_id")
    private IssueStatus fromStatus;

    @ManyToOne
    @JoinColumn(name = "to_status_id")
    private IssueStatus toStatus;


    @ManyToOne
    @JoinColumn(name = "transition_by_user_id")
    private User user;

    @Column(name = "transitioned_at")
    private LocalDateTime transitionedAt;




}
