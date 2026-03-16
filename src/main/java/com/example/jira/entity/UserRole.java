package com.example.jira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "user_roles")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private int roleId;

    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    private String roleName;

    @OneToMany(mappedBy = "role")
    private Set<UserRoleAssignment> userRoleAssignments;

    @OneToMany(mappedBy = "role")
    private Set<RolePermission> rolePermissions;

    @OneToMany(mappedBy = "role")
    private Set<ProjectMember> projectMembers;
}
