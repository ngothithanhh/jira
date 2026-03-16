package com.example.jira.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Embeddable
@Getter @Setter
public class SprintIssueId {
    private int sprintId, issueId;
    public SprintIssueId() {
    }

    public SprintIssueId(int sprintId, int issueId) {
        this.sprintId = sprintId;
        this.issueId = issueId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SprintIssueId that)) return false;
        return sprintId == that.sprintId && issueId == that.issueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sprintId, issueId);
    }
}
