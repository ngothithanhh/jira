package com.example.jira.config;

import com.example.jira.entity.*;
import com.example.jira.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    public void run(String... args) throws Exception {
        log.info("Checking and seeding database...");

        // 1. Khởi tạo các Permission cơ bản (Cả Global và Project)
        Permission createProjectPerm = seedPermission("CREATE_PROJECT");
        Permission editProjectPerm = seedPermission("EDIT_PROJECT");
        Permission deleteProjectPerm = seedPermission("DELETE_PROJECT");
        Permission manageUsersPerm = seedPermission("MANAGE_USERS");
        
        Permission addMemberPerm = seedPermission("ADD_MEMBER");
        Permission removeMemberPerm = seedPermission("REMOVE_MEMBER");
        Permission createIssuePerm = seedPermission("CREATE_ISSUE");
        Permission editIssuePerm = seedPermission("EDIT_ISSUE");
        Permission deleteIssuePerm = seedPermission("DELETE_ISSUE");

        // 2. Khởi tạo Role Global (SYSTEM_ADMIN)
        Optional<UserRole> sysAdminOpt = roleRepository.findByRoleName("SYSTEM_ADMIN");
        UserRole sysAdminRole;
        if (sysAdminOpt.isEmpty()) {
            sysAdminRole = new UserRole();
            sysAdminRole.setRoleName("SYSTEM_ADMIN");
            sysAdminRole = roleRepository.save(sysAdminRole);
            log.info("Seeded SYSTEM_ADMIN role.");

            // Cấp toàn bộ quyền Global cho Admin
            assignPermissionToRole(sysAdminRole, createProjectPerm);
            assignPermissionToRole(sysAdminRole, editProjectPerm);
            assignPermissionToRole(sysAdminRole, deleteProjectPerm);
            assignPermissionToRole(sysAdminRole, manageUsersPerm);
        } else {
            sysAdminRole = sysAdminOpt.get();
        }

        // 3. Khởi tạo Role Dự án (PROJECT_MANAGER và DEVELOPER)
        Optional<UserRole> pmOpt = roleRepository.findByRoleName("PROJECT_MANAGER");
        if (pmOpt.isEmpty()) {
            UserRole pmRole = new UserRole();
            pmRole.setRoleName("PROJECT_MANAGER");
            pmRole = roleRepository.save(pmRole);
            log.info("Seeded PROJECT_MANAGER role.");

            // Cấp quyền quản lý dự án cho PM
            assignPermissionToRole(pmRole, editProjectPerm);
            assignPermissionToRole(pmRole, addMemberPerm);
            assignPermissionToRole(pmRole, removeMemberPerm);
            assignPermissionToRole(pmRole, createIssuePerm);
            assignPermissionToRole(pmRole, editIssuePerm);
            assignPermissionToRole(pmRole, deleteIssuePerm);
        }

        Optional<UserRole> devOpt = roleRepository.findByRoleName("DEVELOPER");
        if (devOpt.isEmpty()) {
            UserRole devRole = new UserRole();
            devRole.setRoleName("DEVELOPER");
            devRole = roleRepository.save(devRole);
            log.info("Seeded DEVELOPER role.");

            // Cấp quyền làm task cho DEV
            assignPermissionToRole(devRole, createIssuePerm);
            assignPermissionToRole(devRole, editIssuePerm);
        }

        // 4. Khởi tạo tài khoản Root Admin
        if (!userRepository.existsByEmail("admin@jira.com")) {
            User rootAdmin = new User();
            rootAdmin.setEmail("admin@jira.com");
            rootAdmin.setUserName("admin");
            rootAdmin.setFullName("System Administrator");
            rootAdmin.setPasswordHash(passwordEncoder.encode("123456"));
            rootAdmin.setCreatedAt(LocalDateTime.now());
            rootAdmin = userRepository.save(rootAdmin);

            // Gán role SYSTEM_ADMIN cho tài khoản này
            UserRoleAssignment assignment = new UserRoleAssignment();
            UserRoleAssignmentId assignmentId = new UserRoleAssignmentId(rootAdmin.getUserId(), sysAdminRole.getRoleId());
            assignment.setId(assignmentId);
            assignment.setUser(rootAdmin);
            assignment.setRole(sysAdminRole);
            assignment.setAssignedAt(LocalDateTime.now());
            assignmentRepository.save(assignment);

            log.info("✅ TẠO TÀI KHOẢN ADMIN MẶC ĐỊNH THÀNH CÔNG: admin@jira.com / 123456");
        } else {
            log.info("Tài khoản admin@jira.com đã tồn tại, bỏ qua bước khởi tạo user.");
        }
    }

    private Permission seedPermission(String permissionName) {
        Optional<Permission> permOpt = permissionRepository.findByPermissionName(permissionName);
        if (permOpt.isPresent()) {
            return permOpt.get();
        }
        Permission p = new Permission();
        p.setPermissionName(permissionName);
        return permissionRepository.save(p);
    }

    private void assignPermissionToRole(UserRole role, Permission permission) {
        if (!rolePermissionRepository.existsByRole_RoleIdAndPermission_PermissionId(role.getRoleId(), permission.getPermissionId())) {
            RolePermission rp = new RolePermission();
            rp.setId(new RolePermissionId(role.getRoleId(), permission.getPermissionId()));
            rp.setRole(role);
            rp.setPermission(permission);
            rolePermissionRepository.save(rp);
        }
    }
}
