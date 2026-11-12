package com.example.jira.dto.admin;

/** Metadata tính từ role thật và các assignment; không thêm scope giả vào entity. */
public record AdminRoleData(int role,String roleName,String scope,boolean reservedName,
                            boolean editablePermissions,long globalAssignmentCount,long projectAssignmentCount) {}
