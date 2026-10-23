package com.thanawat.flakesince.apikey;

import com.jayway.jsonpath.JsonPath;
import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiKeyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired ApiKeyRepository apiKeys;

    @Test
    void createsKeyAndStoresOnlyItsHash() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));

        String body = mvc.perform(post("/api/projects/" + project.getId() + "/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "github-actions"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("github-actions"))
                .andReturn().getResponse().getContentAsString();

        String key = JsonPath.read(body, "$.key");
        assertTrue(key.startsWith("fs_live_"));

        ApiKey stored = apiKeys.findByKeyHash(ApiKeyService.hash(key)).orElseThrow();
        assertNotEquals(key, stored.getKeyHash());
        assertTrue(stored.isActive());
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        mvc.perform(post("/api/projects/999999/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "github-actions"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankLabelReturns400() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));

        mvc.perform(post("/api/projects/" + project.getId() + "/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.label").exists());
    }
}