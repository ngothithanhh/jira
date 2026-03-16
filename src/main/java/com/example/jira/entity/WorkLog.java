package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_logs")
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class WorkLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worklog_id")
    private int worklogId;

//    @Column(name = "issue_id")
//    private int issueId;
    @ManyToOne
    @JoinColumn(name = "issue_id")
    private Issue issue;
//
//    @Column(name = "user_id")
//    private int userId;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "time_spent")
    private int timeSpent;

    private String comment;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;


}
