package com.example.jira.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "board_column_statuses")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class BoardColumnStatus {
    @EmbeddedId
    private BoardColumnStatusId id;


    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("columnId")
    @JoinColumn(name = "column_id")
    private BoardColumn boardColumn;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("statusId")
    @JoinColumn(name = "status_id")
    private IssueStatus issueStatus;
}
