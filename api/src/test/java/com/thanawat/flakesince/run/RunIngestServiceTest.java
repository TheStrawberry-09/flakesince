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
import com.thanawat.flakesince.detection.FlakyStatus;
import com.thanawat.flakesince.detection.FlakyStatusRepository;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RunIngestServiceTest {

    @Autowired RunIngestService ingest;
    @Autowired ProjectRepository projects;
    @Autowired TestCaseRepository testCases;
    @Autowired TestResultRepository results;
    @Autowired FlakyStatusRepository statuses;

    private RunUploadRequest run(Long projectId, String providerRunId) {
        return new RunUploadRequest(projectId, "abc123", "main", providerRunId, 1,
                Instant.parse("2026-10-23T09:00:00Z"), List.of(
                new TestResultUpload("DemoTests", "additionWorks()", TestStatus.PASSED, 3, null),
                new TestResultUpload("DemoTests", "flakyRandomTest()", TestStatus.FAILED, 5,
                        "expected: <true> but was: <false>")));
    }

    private RunUploadRequest runWith(Long projectId, String providerRunId, TestStatus flakyStatus) {
        return new RunUploadRequest(projectId, "abc123", "main", providerRunId, 1,
                Instant.parse("2026-10-23T09:00:00Z"), List.of(
                new TestResultUpload("DemoTests", "additionWorks()", TestStatus.PASSED, 3, null),
                new TestResultUpload("DemoTests", "flakyRandomTest()", flakyStatus, 5, null)));
    }

    private Long testCaseId(Long projectId, String name) {
        return testCases.findByProjectIdAndSuiteAndName(projectId, "DemoTests", name).orElseThrow().getId();
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

    @Test
    void passAndFailOnSameCommitIsMarkedFlaky() {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();

        ingest.ingest(runWith(projectId, "4000", TestStatus.PASSED));
        ingest.ingest(runWith(projectId, "4001", TestStatus.FAILED));

        FlakyStatus flaky = statuses.findById(testCaseId(projectId, "flakyRandomTest()")).orElseThrow();
        assertTrue(flaky.isFlaky());
        assertTrue(flaky.isSignalSameCommit());
        assertFalse(flaky.isSignalFailureRate());   // only 2 runs: too few for Signal D
        assertNull(flaky.getFailureRate());         // not judged yet
        assertEquals(0.5, flaky.getScore());        // 1 of 2 signals
        assertNotNull(flaky.getFirstDetectedAt());

        FlakyStatus stable = statuses.findById(testCaseId(projectId, "additionWorks()")).orElseThrow();
        assertFalse(stable.isFlaky());
        assertEquals(0.0, stable.getScore());
    }

    @Test
    void duplicateRunDoesNotAddVerdicts() {
        Long projectId = projects.save(new Project("Flaky Demo", "flaky-demo")).getId();

        ingest.ingest(runWith(projectId, "5000", TestStatus.PASSED));
        long before = statuses.count();
        ingest.ingest(runWith(projectId, "5000", TestStatus.PASSED));

        assertEquals(before, statuses.count());
    }
}