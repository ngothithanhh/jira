package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "issue_versions")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class IssueVersion {
    @EmbeddedId
    private IssueVersionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("issueId")
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("versionId")
    @JoinColumn(name = "version_id")
    private ProjectVersion projectVersion;

}
