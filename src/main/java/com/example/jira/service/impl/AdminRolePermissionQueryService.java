package com.example.jira.service.impl;

import com.example.jira.dto.admin.RolePermissionData.*;
import com.example.jira.entity.Permission;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AdminRolePermissionQueryService {
    private final GlobalSecurity global;
    private final UserRoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository grants;
    private final AdminRoleQueryService catalog;

    /**
     * Admin mở ma trận: đọc role/count, permission và cặp grant trong cùng snapshot PostgreSQL.
     * Chỉ trả ID permission lưu thật, không suy default theo tên hoặc tải từng role bằng N truy vấn.
     */
    public Matrix matrix(){
        global.assertSystemAdmin();
        var roleRows=catalog.list();
        var permissionRows=permissions.findAll(org.springframework.data.domain.Sort.by("permissionId"));
        RolePolicy.assertUniquePermissions(permissionRows);
        Map<Integer,List<Integer>> byRole=new HashMap<>();
        for(var pair:grants.matrixPairs())byRole.computeIfAbsent(((Number)pair[0]).intValue(),ignored->new ArrayList<>()).add(((Number)pair[1]).intValue());
        var rows=roleRows.stream().map(r->new MatrixRole(r.role(),r.roleName(),r.scope(),r.reservedName(),r.editablePermissions(),
                r.globalAssignmentCount(),r.projectAssignmentCount(),List.copyOf(byRole.getOrDefault(r.role(),List.of())))).toList();
        var metadata=permissionRows.stream().map(p->new PermissionMetadata(p.getPermissionId(),p.getPermissionName(),
                RolePolicy.supported(p.getPermissionName()),RolePolicy.assignable(p.getPermissionName()),"VIEW_PROJECT".equals(p.getPermissionName()))).toList();
        return new Matrix(rows,metadata);
    }

    /** roleId từ user_roles, không phải userId; kiểm admin trước tìm, thiếu404, chỉ đọc stored grant trong transaction. */
    public RoleSnapshot snapshot(int roleId){
        global.assertSystemAdmin();
        var role=roles.findByRoleId(roleId).orElseThrow(()->new NotFoundException("Không tìm thấy vai trò"));
        RolePolicy.assertUniquePermissions(permissions.findAll());
        return new RoleSnapshot(roleId,role.getRoleName(),List.copyOf(grants.permissionIds(roleId)));
    }
}
