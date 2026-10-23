package com.thanawat.flakesince.run;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.testcase.TestCaseRepository;
import com.thanawat.flakesince.testcase.TestResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RunControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired TestCaseRepository testCases;
    @Autowired TestResultRepository results;

    private String runJson(Long projectId, String providerRunId) {
        return """
                {
                  "projectId": %d,
                  "commitSha": "abc123",
                  "branch": "main",
                  "providerRunId": "%s",
                  "runAttempt": 1,
                  "startedAt": "2026-10-23T09:00:00Z",
                  "results": [
                    {"suite": "DemoTests", "name": "additionWorks()", "status": "PASSED", "durationMs": 3},
                    {"suite": "DemoTests", "name": "uppercaseWorks()", "status": "PASSED", "durationMs": 2},
                    {"suite": "DemoTests", "name": "flakyRandomTest()", "status": "FAILED", "durationMs": 5,
                     "failureMessage": "expected: <true> but was: <false>"}
                  ]
                }
                """.formatted(projectId, providerRunId);
    }

    @Test
    void savesRunAndReusesTestCasesAcrossRuns() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(runJson(project.getId(), "1000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.resultsSaved").value(3));

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(runJson(project.getId(), "1001")))
                .andExpect(status().isCreated());

        assertEquals(3, testCases.count());
        assertEquals(6, results.count());
    }

    @Test
    void skipsDuplicateRunAttempt() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        String body = runJson(project.getId(), "2000");

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.resultsSaved").value(0));

        assertEquals(3, results.count());
    }

    @Test
    void rejectsResultWithoutSuite() throws Exception {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        String body = runJson(project.getId(), "3000")
                .replace("\"suite\": \"DemoTests\", \"name\": \"additionWorks()\"",
                        "\"suite\": \"\", \"name\": \"additionWorks()\"");

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['results[0].suite']").exists());
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(runJson(999999L, "4000")))
                .andExpect(status().isNotFound());
    }
}