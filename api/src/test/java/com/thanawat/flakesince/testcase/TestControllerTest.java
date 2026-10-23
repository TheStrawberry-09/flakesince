package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.run.RunIngestService;
import com.thanawat.flakesince.run.RunUploadRequest;
import com.thanawat.flakesince.run.TestResultUpload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired TestCaseRepository testCases;
    @Autowired RunIngestService ingest;

    private void upload(Long projectId, String runId, int attempt, String sha, String startedAt, TestStatus flaky) {
        ingest.ingest(new RunUploadRequest(projectId, sha, "main", runId, attempt, Instant.parse(startedAt), List.of(
                new TestResultUpload("DemoTests", "additionWorks()", TestStatus.PASSED, 3, null),
                new TestResultUpload("DemoTests", "flakyRandomTest()", flaky, 5,
                        flaky == TestStatus.FAILED ? "expected: <true> but was: <false>" : null))));
    }

    @Test
    void historyIsOldestFirstWithCommitInfo() throws Exception {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();
        upload(projectId, "1001", 1, "def456", "2026-10-23T10:00:00Z", TestStatus.FAILED);
        upload(projectId, "1000", 1, "abc123", "2026-10-23T09:00:00Z", TestStatus.FAILED);
        upload(projectId, "1000", 2, "abc123", "2026-10-23T09:10:00Z", TestStatus.PASSED);

        Long flakyId = testCases.findByProjectIdAndSuiteAndName(projectId, "DemoTests", "flakyRandomTest()")
                .orElseThrow().getId();

        mvc.perform(get("/api/tests/" + flakyId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].commitSha").value("abc123"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[1].runAttempt").value(2))
                .andExpect(jsonPath("$[1].status").value("PASSED"))
                .andExpect(jsonPath("$[2].commitSha").value("def456"));
    }

    @Test
    void testListShowsLatestStatusAndPages() throws Exception {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();
        upload(projectId, "1000", 1, "abc123", "2026-10-23T09:00:00Z", TestStatus.FAILED);
        upload(projectId, "1000", 2, "abc123", "2026-10-23T09:10:00Z", TestStatus.PASSED);

        mvc.perform(get("/api/projects/" + projectId + "/tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].name").value("additionWorks()"))
                .andExpect(jsonPath("$.items[1].name").value("flakyRandomTest()"))
                .andExpect(jsonPath("$.items[1].latestStatus").value("PASSED"));

        mvc.perform(get("/api/projects/" + projectId + "/tests?size=1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void unknownTestReturns404() throws Exception {
        mvc.perform(get("/api/tests/999999/history"))
                .andExpect(status().isNotFound());
    }
}