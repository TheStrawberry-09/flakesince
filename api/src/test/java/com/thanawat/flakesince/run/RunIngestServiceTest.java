package com.thanawat.flakesince.run;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.testcase.TestCaseRepository;
import com.thanawat.flakesince.testcase.TestResultRepository;
import com.thanawat.flakesince.testcase.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RunIngestServiceTest {

    @Autowired RunIngestService ingest;
    @Autowired ProjectRepository projects;
    @Autowired TestCaseRepository testCases;
    @Autowired TestResultRepository results;

    private RunUploadRequest run(Long projectId, String providerRunId) {
        return new RunUploadRequest(projectId, "abc123", "main", providerRunId, 1,
                Instant.parse("2026-10-23T09:00:00Z"), List.of(
                new TestResultUpload("DemoTests", "additionWorks()", TestStatus.PASSED, 3, null),
                new TestResultUpload("DemoTests", "flakyRandomTest()", TestStatus.FAILED, 5,
                        "expected: <true> but was: <false>")));
    }

    @Test
    void savesRunAndReusesTestCasesAcrossRuns() {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();

        IngestResult first = ingest.ingest(run(projectId, "1000"));
        ingest.ingest(run(projectId, "1001"));

        assertFalse(first.duplicate());
        assertEquals(2, first.resultsSaved());
        assertEquals(2, testCases.count());
        assertEquals(4, results.count());
    }

    @Test
    void skipsDuplicateRunAttempt() {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();

        IngestResult first = ingest.ingest(run(projectId, "2000"));
        IngestResult second = ingest.ingest(run(projectId, "2000"));

        assertTrue(second.duplicate());
        assertEquals(0, second.resultsSaved());
        assertEquals(first.runId(), second.runId());
        assertEquals(2, results.count());
    }

    @Test
    void unknownProjectIsRejected() {
        assertThrows(ResponseStatusException.class, () -> ingest.ingest(run(999999L, "3000")));
    }
}