package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_members")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProjectMember {


    @EmbeddedId
    private ProjectMemberId id;

//    @Column(name = "project_id")
//    private int projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    private Project project;

//    @Column(name = "user_id")
//    private int userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

//    @Column(name = "role_id")
//    private int roleId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private UserRole role;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;



}
