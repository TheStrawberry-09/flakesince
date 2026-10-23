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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiKeyControllerTest {

    @Autowired ApiKeyService apiKeyService;
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

    @Test
    void revokesKey() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        Long keyId = apiKeyService.create(project.getId(), "github-actions").id();

        mvc.perform(delete("/api/api-keys/" + keyId))
                .andExpect(status().isNoContent());

        assertFalse(apiKeys.findById(keyId).orElseThrow().isActive());
    }

    @Test
    void revokingTwiceKeepsTheFirstRevokeTime() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        Long keyId = apiKeyService.create(project.getId(), "github-actions").id();

        mvc.perform(delete("/api/api-keys/" + keyId)).andExpect(status().isNoContent());
        var firstRevokedAt = apiKeys.findById(keyId).orElseThrow().getRevokedAt();

        mvc.perform(delete("/api/api-keys/" + keyId)).andExpect(status().isNoContent());

        assertEquals(firstRevokedAt, apiKeys.findById(keyId).orElseThrow().getRevokedAt());
    }

    @Test
    void revokingUnknownKeyReturns404() throws Exception {
        mvc.perform(delete("/api/api-keys/999999"))
                .andExpect(status().isNotFound());
    }
}