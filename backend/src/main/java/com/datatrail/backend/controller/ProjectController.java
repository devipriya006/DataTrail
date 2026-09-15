package com.datatrail.backend.controller;

import com.datatrail.backend.entity.Project;
import com.datatrail.backend.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Project createProject(@RequestBody Map<String, Object> request) {

        Long ownerId = Long.valueOf(
                request.get("ownerId").toString()
        );

        String projectName = (String) request.get("projectName");
        String description = (String) request.get("description");

        return projectService.createProject(
                ownerId,
                projectName,
                description
        );
    }

    @GetMapping
    public List<Project> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    public Project getProjectById(@PathVariable Long id) {
        return projectService.getProjectById(id);
    }

    @GetMapping("/owner/{ownerId}")
    public List<Project> getProjectsByOwner(
            @PathVariable Long ownerId) {

        return projectService.getProjectsByOwner(ownerId);
    }

    @DeleteMapping("/{id}")
    public String deleteProject(@PathVariable Long id) {

        projectService.deleteProject(id);

        return "Project deleted successfully";
    }
}
