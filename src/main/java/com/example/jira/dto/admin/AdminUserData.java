package com.example.jira.dto.admin;
import com.example.jira.dto.user_role.UserRoleResponse;
import java.time.LocalDateTime;
import java.util.List;
public final class AdminUserData {
    private AdminUserData() {}
    public record UserRow(int userId,String userName,String fullName,String email,boolean enabled,
                          LocalDateTime createdAt,List<UserRoleResponse> roles,long projectMembershipCount) {}
    public record UserPage(List<UserRow> items,int page,int size,long totalItems,int totalPages) {}
}
