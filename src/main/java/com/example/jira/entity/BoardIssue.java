package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "board_issues")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class BoardIssue {

    @EmbeddedId
    private BoardIssueId id;

//    @Column(name = "board_id")
//    private int boardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("boardId")
    @JoinColumn(name = "board_id")
    private Board board;

//    @Column(name = "column_id")
//    private int columnId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id")
    private BoardColumn column;

//    @Column(name = "issue_id")
//    private int issueId;
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("issueId")
    @JoinColumn(name = "issue_id")
    private Issue issue;

    private int position;
}
