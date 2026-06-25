package com.example.jira.controller.project;

import com.example.jira.dto.project.CreateProjectRequest;
import com.example.jira.dto.project.ProjectSummary;
import com.example.jira.dto.project.UpdateProjectRequest;
import com.example.jira.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping("/create")
    public ProjectSummary create(@RequestBody CreateProjectRequest request){
        return projectService.createProject(request);
    }

    @GetMapping("/getList")
    public List<ProjectSummary> getProjects(){
        return projectService.getProjects();
    }

    @GetMapping("/{id}")
    public ProjectSummary getProject(@PathVariable int id){
        return projectService.getProject(id);
    }

    @PatchMapping("/{id}")
    public ProjectSummary updateProject(@PathVariable int id, @RequestBody UpdateProjectRequest request){
        return projectService.updateProject(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteProject(@PathVariable int id){
        projectService.deleteProject(id);
    }
}
