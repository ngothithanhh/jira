package com.example.jira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    //global role assignment
    @OneToMany(mappedBy = "role")
    @JsonIgnore
    private Set<UserRoleAssignment> userRoleAssignments;

    //role permission
    @OneToMany(mappedBy = "role")
    @JsonIgnore
    private Set<RolePermission> rolePermissions;

    //role project
    @OneToMany(mappedBy = "role")
    @JsonIgnore
    private Set<ProjectMember> projectMembers;
}
