package com.thanawat.flakesince.project;

import java.time.Instant;

public record ProjectResponse(Long id, String name, String slug, Instant createdAt) {

    static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getSlug(), project.getCreatedAt());
    }
}