package com.example.jira.service.impl;

import com.example.jira.dto.admin.AdminRoleData;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminRoleQueryService {
    private final GlobalSecurity global;
    private final UserRoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private final ProjectMemberRepository members;

    /** Admin đọc role và hai số đếm độc lập; ba truy vấn theo nhóm tránh N+1 và không nhân đôi count khi join. */
    public List<AdminRoleData> list() {
        global.assertSystemAdmin();
        var rows=roles.findAll(org.springframework.data.domain.Sort.by("roleId"));
        if(rows.isEmpty())return List.of();
        var globals=counts(assignments.countByRole());
        var projects=counts(members.countByRole());
        return rows.stream().map(r->new AdminRoleData(r.getRoleId(),r.getRoleName(),
            RolePolicy.system(r.getRoleName())?"GLOBAL":"PROJECT",RolePolicy.reserved(r.getRoleName()),
            !RolePolicy.system(r.getRoleName()),globals.getOrDefault(r.getRoleId(),0L),projects.getOrDefault(r.getRoleId(),0L))).toList();
    }
    /** Mỗi projection là role_id và count thật; không tải user/email/hash hoặc quan hệ lazy. */
    private Map<Integer,Long> counts(List<Object[]> rows) {
        Map<Integer,Long> result=new HashMap<>();
        for(var row:rows)result.put(((Number)row[0]).intValue(),((Number)row[1]).longValue());
        return result;
    }
}
