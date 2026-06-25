package com.example.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_role_assignments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignment {
    @EmbeddedId
    private UserRoleAssignmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    @JsonIgnore
    private UserRole role;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
