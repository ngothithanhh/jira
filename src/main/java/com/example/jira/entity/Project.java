package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.cglib.core.Local;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "projects")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private int projectId;

    @Column(name = "project_name",length = 100)
    private String projectName;

    @Column(name = "project_key", length = 10, unique = true, nullable = false)
    private String projectKey;

    private String description;

    @ManyToOne
    @JoinColumn(name = "lead_user_id")
    private User lead;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "project")
    private Set<ProjectMember> projectMembers;

    @OneToMany(mappedBy = "project")
    private Set<Board> boards;

    @OneToMany(mappedBy = "project")
    private Set<Sprint> sprints;

    @OneToMany(mappedBy = "project")
    private Set<ProjectVersion> projectVersions;

    @OneToMany(mappedBy = "project")
    private Set<Issue> issues;
}
