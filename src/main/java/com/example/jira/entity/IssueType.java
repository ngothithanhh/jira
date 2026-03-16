package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "issue_types")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class IssueType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_type_id")
    private int issueTypeId;

    @Column(name = "type_name", length = 50, unique = true)
    private String typeName;

    @OneToMany(mappedBy = "issueType")
    private Set<Issue> issues;
}
