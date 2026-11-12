package com.example.jira.controller.project;

import com.example.jira.dto.project.MemberCandidate;
import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.service.AuthService;
import com.example.jira.service.PermissionService;
import com.example.jira.service.ProjectMemberService;
import com.example.jira.enums.PermissionName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {
    public record ChangeRoleRequest(@Positive int roleId) { }
    public record CandidateSearch(@NotBlank @Email @Size(max = 100) String email) {
        public CandidateSearch {
            email = email == null ? null : email.trim();
        }
    }
    private final ProjectMemberService projectMemberService;
    private final PermissionService permissionService;
    private final AuthService authService;

    /** roleId thuộc user_roles; service đổi đúng member project/user, không cấp role toàn cục. */
    @PutMapping("/{userId}/role")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void updateRole(@PathVariable @Positive int projectId, @PathVariable @Positive int userId, @Valid @RequestBody ChangeRoleRequest body) {
        requirePermission(projectId, PermissionName.ADD_MEMBER);
        projectMemberService.updateRole(projectId,userId,body.roleId());
    }

    @GetMapping
    public List<ProjectMemberResponse> getMembers(@PathVariable @Positive int projectId) {
        return projectMemberService.getMembers(projectId);
    }

    @PostMapping("/candidates/search")
    public List<MemberCandidate> findCandidate(@PathVariable @Positive int projectId, @Valid @RequestBody CandidateSearch request) {
        return projectMemberService.findCandidate(projectId, request.email());
    }

    @DeleteMapping("/{userId}")
    public void removeMember(@PathVariable @Positive int projectId, @PathVariable @Positive int userId) {
        requirePermission(projectId, PermissionName.DELETE_MEMBER);
        projectMemberService.removeMember(projectId, userId);
    }

    /** Lọc request trước transaction; service kiểm lại quyền sau khi giữ khóa project. */
    private void requirePermission(int projectId, PermissionName permission) {
        int actorId = authService.getCurrentUser().getId();
        if (!permissionService.checkPermission(actorId, permission.name(), projectId)) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện trên dự án này");
        }
    }
}
