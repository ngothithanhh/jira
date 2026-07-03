package com.example.jira.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProjectIssueTypeId implements Serializable {
    private int projectId;
    private int issueTypeId;

    public ProjectIssueTypeId() {}

    public ProjectIssueTypeId(int projectId, int issueTypeId) {
        this.projectId = projectId;
        this.issueTypeId = issueTypeId;
    }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public int getIssueTypeId() { return issueTypeId; }
    public void setIssueTypeId(int issueTypeId) { this.issueTypeId = issueTypeId; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProjectIssueTypeId that)) return false;
        return projectId == that.projectId && issueTypeId == that.issueTypeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, issueTypeId);
    }
}
