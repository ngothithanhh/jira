package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.jira.enums.SprintState;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "sprints")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sprint_id")
    private int sprintId;

//    @Column(name = "project_id")
//    private int projectId;
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "sprint_name", length = 100)
    private String sprintName;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    private String goal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private SprintState state;

    @OneToMany(mappedBy = "sprint")
    private Set<SprintIssue> sprintIssues;


}
