package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private int userId;

    @Column(name = "username", length = 50, nullable = false)
    private String userName;

    @Column(unique = true, nullable = false,length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user")
    private Set<ProjectMember> projectMembers;

    @OneToMany(mappedBy = "user")
    private Set<WorkLog> workLogs;

    @OneToMany(mappedBy = "lead")
    private Set<Project> projects;


    @OneToMany(mappedBy = "user")
    private Set<IssueTransition> issueTransitions;

    @OneToMany(mappedBy = "user")
    private Set<IssueComment> issueComments;

    @OneToMany(mappedBy = "user")
    private Set<IssueAttachment> issueAttachments;

    @OneToMany(mappedBy = "user")
    private Set<RefreshToken> refreshTokens;

    @OneToMany(mappedBy = "user")
    private Set<AuditLog> auditLogs;

}
