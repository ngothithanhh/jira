package com.example.jira.config;

import com.example.jira.entity.Permission;
import com.example.jira.entity.RolePermission;
import com.example.jira.entity.RolePermissionId;
import com.example.jira.entity.User;
import com.example.jira.entity.UserRole;
import com.example.jira.entity.UserRoleAssignment;
import com.example.jira.entity.UserRoleAssignmentId;
import com.example.jira.enums.PermissionName;
import com.example.jira.repository.PermissionRepository;
import com.example.jira.repository.RolePermissionRepository;
import com.example.jira.repository.UserRepository;
import com.example.jira.repository.UserRoleAssignmentRepository;
import com.example.jira.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and seeding database...");

        migrateLegacyPermission("EDIT_PROJECT", PermissionName.UPDATE_PROJECT);
        migrateLegacyPermission("REMOVE_MEMBER", PermissionName.DELETE_MEMBER);
        migrateLegacyPermission("MANAGE_USERS", PermissionName.DELETE_USER);

        Permission createProject = seedPermission(PermissionName.CREATE_PROJECT);
        Permission updateProject = seedPermission(PermissionName.UPDATE_PROJECT);
        Permission deleteProject = seedPermission(PermissionName.DELETE_PROJECT);
        Permission deleteUser = seedPermission(PermissionName.DELETE_USER);
        Permission viewProject = seedPermission(PermissionName.VIEW_PROJECT);
        Permission addMember = seedPermission(PermissionName.ADD_MEMBER);
        Permission deleteMember = seedPermission(PermissionName.DELETE_MEMBER);
        Permission createIssue = seedPermission(PermissionName.CREATE_ISSUE);
        Permission editIssue = seedPermission(PermissionName.EDIT_ISSUE);
        Permission deleteIssue = seedPermission(PermissionName.DELETE_ISSUE);
        Permission manageBoard = seedPermission(PermissionName.MANAGE_BOARD);
        Permission manageSprint = seedPermission(PermissionName.MANAGE_SPRINT);
        Permission manageWorkflow = seedPermission(PermissionName.MANAGE_WORKFLOW);
        seedPermission(PermissionName.CREATE_ISSUE_TYPE);

        UserRole systemAdmin = seedRole("SYSTEM_ADMIN");
        assignPermissionToRole(systemAdmin, createProject);
        assignPermissionToRole(systemAdmin, updateProject);
        assignPermissionToRole(systemAdmin, deleteProject);
        assignPermissionToRole(systemAdmin, deleteUser);

        UserRole projectManager = seedRole("PROJECT_MANAGER");
        assignPermissionToRole(projectManager, updateProject);
        assignPermissionToRole(projectManager, viewProject);
        assignPermissionToRole(projectManager, addMember);
        assignPermissionToRole(projectManager, deleteMember);
        assignPermissionToRole(projectManager, createIssue);
        assignPermissionToRole(projectManager, editIssue);
        assignPermissionToRole(projectManager, deleteIssue);
        assignPermissionToRole(projectManager, manageBoard);
        assignPermissionToRole(projectManager, manageSprint);
        assignPermissionToRole(projectManager, manageWorkflow);

        UserRole scrumMaster = seedRole("SCRUM_MASTER");
        assignPermissionToRole(scrumMaster, viewProject);
        assignPermissionToRole(scrumMaster, manageBoard);
        assignPermissionToRole(scrumMaster, manageSprint);
        assignPermissionToRole(scrumMaster, manageWorkflow);
        assignPermissionToRole(scrumMaster, createIssue);
        assignPermissionToRole(scrumMaster, editIssue);

        UserRole developer = seedRole("DEVELOPER");
        assignPermissionToRole(developer, viewProject);
        assignPermissionToRole(developer, createIssue);
        assignPermissionToRole(developer, editIssue);

        seedDefaultAdmin(systemAdmin);
    }

    private Permission seedPermission(PermissionName permissionName) {
        return seedPermission(permissionName.name());
    }

    private Permission seedPermission(String permissionName) {
        return permissionRepository.findByPermissionName(permissionName).orElseGet(() -> {
            Permission permission = new Permission();
            permission.setPermissionName(permissionName);
            return permissionRepository.save(permission);
        });
    }

    private UserRole seedRole(String roleName) {
        return roleRepository.findByRoleName(roleName).orElseGet(() -> {
            UserRole role = new UserRole();
            role.setRoleName(roleName);
            log.info("Seeded {} role.", roleName);
            return roleRepository.save(role);
        });
    }

    private void migrateLegacyPermission(String legacyName, PermissionName canonicalName) {
        Optional<Permission> legacyPermission = permissionRepository.findByPermissionName(legacyName);
        if (legacyPermission.isEmpty()) {
            return;
        }

        Optional<Permission> canonicalPermission = permissionRepository.findByPermissionName(canonicalName.name());
        if (canonicalPermission.isEmpty()) {
            Permission permission = legacyPermission.get();
            permission.setPermissionName(canonicalName.name());
            permissionRepository.save(permission);
            log.info("Renamed permission {} to {}.", legacyName, canonicalName);
            return;
        }

        Permission oldPermission = legacyPermission.get();
        Permission newPermission = canonicalPermission.get();
        List<RolePermission> oldAssignments = rolePermissionRepository.findByPermission_PermissionId(oldPermission.getPermissionId());
        for (RolePermission assignment : oldAssignments) {
            assignPermissionToRole(assignment.getRole(), newPermission);
        }
        rolePermissionRepository.deleteAll(oldAssignments);
        permissionRepository.delete(oldPermission);
        log.info("Merged permission {} into {}.", legacyName, canonicalName);
    }

    private void assignPermissionToRole(UserRole role, Permission permission) {
        if (rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionId(role.getRoleId(), permission.getPermissionId())) {
            return;
        }
        RolePermission assignment = new RolePermission();
        assignment.setId(new RolePermissionId(role.getRoleId(), permission.getPermissionId()));
        assignment.setRole(role);
        assignment.setPermission(permission);
        rolePermissionRepository.save(assignment);
    }

    private void seedDefaultAdmin(UserRole systemAdmin) {
        if (userRepository.existsByEmail("admin@jira.com")) {
            return;
        }

        User admin = new User();
        admin.setEmail("admin@jira.com");
        admin.setUserName("admin");
        admin.setFullName("System Administrator");
        admin.setPasswordHash(passwordEncoder.encode("123456"));
        admin.setCreatedAt(LocalDateTime.now());
        admin = userRepository.save(admin);

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setId(new UserRoleAssignmentId(admin.getUserId(), systemAdmin.getRoleId()));
        assignment.setUser(admin);
        assignment.setRole(systemAdmin);
        assignment.setAssignedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
    }
}
