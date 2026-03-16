package com.example.jira.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Embeddable
@Getter @Setter
public class IssueVersionId {
    private int issueId, versionId;

    public IssueVersionId() {
    }

    public IssueVersionId(int issueId, int versionId) {
        this.issueId = issueId;
        this.versionId = versionId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IssueVersionId that)) return false;
        return issueId == that.issueId && versionId == that.versionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(issueId, versionId);
    }
}
