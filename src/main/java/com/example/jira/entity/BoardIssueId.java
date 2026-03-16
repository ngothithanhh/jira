package com.example.jira.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Embeddable
@Getter
@Setter
public class BoardIssueId {
    private int boardId;
    private int issueId;

    public BoardIssueId() {
    }

    public BoardIssueId(int boardId, int issueId) {
        this.boardId = boardId;
        this.issueId = issueId;
    }

//    public int getBoardId() {
//        return boardId;
//    }
//
//    public void setBoardId(int boardId) {
//        this.boardId = boardId;
//    }
//
//    public int getIssueId() {
//        return issueId;
//    }
//
//    public void setIssueId(int issueId) {
//        this.issueId = issueId;
//    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoardIssueId that)) return false;
        return boardId == that.boardId && issueId == that.issueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardId, issueId);
    }
}
