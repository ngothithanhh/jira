package com.example.jira.controller.project;

import com.example.jira.dto.project.AddMemberRequest;
import com.example.jira.dto.project.ProjectMemberResponse;
import com.example.jira.service.AuthService;
import com.example.jira.service.PermissionService;
import com.example.jira.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {
    private final ProjectMemberService projectMemberService;
    private final PermissionService permissionService;
    private final AuthService authService;

    @PostMapping
    public void addMember(@PathVariable int projectId, @RequestBody AddMemberRequest request){

        int userId = authService.getCurrentUser().getId();

        permissionService.checkPermission(userId, "ADD_MEMBER",projectId);

        projectMemberService.addMember(projectId,request);
    }

    @GetMapping
    public List<ProjectMemberResponse> getMembers(@PathVariable int projectId){
        return projectMemberService.getMembers(projectId);
    }

    @DeleteMapping("/{userId}")
    public void removeMember(@PathVariable int projectId, @PathVariable int userId){
        int currentUserId = authService.getCurrentUser().getId();

        permissionService.checkPermission(currentUserId, "REMOVE_MEMBER", projectId);
        projectMemberService.removeMember(projectId,userId);
    }

}
