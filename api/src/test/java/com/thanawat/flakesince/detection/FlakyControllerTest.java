package com.thanawat.flakesince.detection;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.run.RunIngestService;
import com.thanawat.flakesince.run.RunUploadRequest;
import com.thanawat.flakesince.run.TestResultUpload;
import com.thanawat.flakesince.testcase.TestStatus;
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
class FlakyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired RunIngestService ingest;

    private void upload(Long projectId, int attempt, String startedAt, TestStatus flaky) {
        ingest.ingest(new RunUploadRequest(projectId, "abc123", "main", "1000", attempt,
                Instant.parse(startedAt), List.of(
                new TestResultUpload("DemoTests", "additionWorks()", TestStatus.PASSED, 3, null),
                new TestResultUpload("DemoTests", "flakyRandomTest()", flaky, 5, null))));
    }

    @Test
    void listsOnlyTheFlakyTest() throws Exception {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();
        upload(projectId, 1, "2026-11-01T09:00:00Z", TestStatus.FAILED);
        upload(projectId, 2, "2026-11-01T09:10:00Z", TestStatus.PASSED);   // re-run, same commit

        mvc.perform(get("/api/projects/" + projectId + "/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("flakyRandomTest()"))
                .andExpect(jsonPath("$[0].score").value(0.5))
                .andExpect(jsonPath("$[0].signals[0]").value("SAME_COMMIT"));
    }

    @Test
    void noFlakyTestsGivesEmptyList() throws Exception {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();
        upload(projectId, 1, "2026-11-01T09:00:00Z", TestStatus.PASSED);

        mvc.perform(get("/api/projects/" + projectId + "/flaky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        mvc.perform(get("/api/projects/999999/flaky"))
                .andExpect(status().isNotFound());
    }
}