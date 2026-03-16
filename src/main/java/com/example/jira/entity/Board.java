package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "boards")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private int boardId;

//    @Column(name = "project_id")
//    private int projectId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "board_name", length = 100)
    private String boardName;

    @Column(name = "board_type", length = 50)
    private String boardType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "board")
    private Set<BoardColumn> boardColumns;

    @OneToMany(mappedBy = "board")
    private Set<BoardIssue> boardIssues;
}
