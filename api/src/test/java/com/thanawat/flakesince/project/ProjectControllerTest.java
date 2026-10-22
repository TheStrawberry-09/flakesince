package com.thanawat.flakesince.project;

import com.thanawat.flakesince.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createsProject() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Flaky Demo", "slug": "flaky-demo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("flaky-demo"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void rejectsInvalidRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "slug": "Bad Slug!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void rejectsDuplicateSlug() throws Exception {
        String body = """
                {"name": "Demo", "slug": "dup"}
                """;
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slug 'dup' is already taken"));
    }
}