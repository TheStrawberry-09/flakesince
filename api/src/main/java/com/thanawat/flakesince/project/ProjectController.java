package com.thanawat.flakesince.project;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        if (projects.existsBySlug(request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug '" + request.slug() + "' is already taken");
        }
        Project saved = projects.save(new Project(request.name(), request.slug()));
        return ProjectResponse.from(saved);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projects.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }
}