package com.thanawat.flakesince.project;

import com.thanawat.flakesince.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectRepositoryTest {

    @Autowired
    ProjectRepository projects;

    @Test
    void savesAndFindsBySlug() {
        projects.save(new Project("Flaky Demo", "flaky-demo"));

        Optional<Project> found = projects.findBySlug("flaky-demo");

        assertTrue(found.isPresent());
        assertEquals("Flaky Demo", found.get().getName());
        assertNotNull(found.get().getId());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void unknownSlugReturnsEmpty() {
        assertTrue(projects.findBySlug("does-not-exist").isEmpty());
    }
}