package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "project_versions")
@NoArgsConstructor
@AllArgsConstructor
@Setter @Getter
public class ProjectVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private int versionId;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "version_name", length = 100)
    private String versionName;

    private boolean released;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    private String description;

    @OneToMany(mappedBy = "projectVersion")
    private Set<IssueVersion> issueVersions;


}
