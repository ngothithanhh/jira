package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "board_columns")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class BoardColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "column_id")
    private int columnId;

    @Column(name = "column_name")
    private String columnName;

    private int position;

    @Column(name = "wip_limit")
    private int wipLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @OneToMany(mappedBy = "column")
    private Set<BoardIssue> boardIssues;

    @OneToMany(mappedBy = "boardColumn")
    private Set<BoardColumnStatus> boardColumnStatuses;

}
