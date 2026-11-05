package com.thanawat.flakesince.detection;

import com.thanawat.flakesince.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FlakyDetectorTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FlakyDetector detector;

    Long projectId;
    Long testId;
    int runCount = 0;
    final OffsetDateTime start = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        projectId = jdbc.queryForObject(
                "INSERT INTO project (name, slug) VALUES ('Detector', 'detector-test') RETURNING id", Long.class);
        testId = jdbc.queryForObject(
                "INSERT INTO test_case (project_id, suite, name) VALUES (?, 'DemoTests', 'x') RETURNING id",
                Long.class, projectId);
    }

    /** One run of a 10-test suite. Each call is one minute newer than the last. */
    Long run(String commit, String branch, int failedInRun) {
        runCount++;
        return jdbc.queryForObject("""
                INSERT INTO ci_run (project_id, provider_run_id, run_attempt, commit_sha, branch, started_at, total, failed)
                VALUES (?, ?, 1, ?, ?, ?, 10, ?) RETURNING id
                """, Long.class, projectId, "run-" + runCount, commit, branch, start.plusMinutes(runCount), failedInRun);
    }

    /** Test x got this status in run runId. */
    void resultIn(Long runId, String status) {
        jdbc.update("INSERT INTO test_result (ci_run_id, test_case_id, status, duration_ms) VALUES (?, ?, ?, 10)",
                runId, testId, status);
    }

    /** Test x got this status in a normal run on main. */
    void result(String commit, String status) {
        boolean fail = status.equals("FAILED") || status.equals("ERROR");
        resultIn(run(commit, "main", fail ? 1 : 0), status);
    }

    // ---------- Signal A: your answers #1-#6 ----------

    @Test
    void sameCommitPassAndFail() {                 // #1
        result("aaa", "PASSED");
        result("aaa", "FAILED");
        assertTrue(detector.sameCommitSignal(testId));
    }

    @Test
    void differentCommitsDoNotCount() {            // #2
        result("aaa", "PASSED");
        result("bbb", "FAILED");
        assertFalse(detector.sameCommitSignal(testId));
    }

    @Test
    void alwaysPassing() {                         // #3
        result("aaa", "PASSED");
        result("aaa", "PASSED");
        result("aaa", "PASSED");
        assertFalse(detector.sameCommitSignal(testId));
    }

    @Test
    void errorCountsAsFail() {                     // #4
        result("aaa", "PASSED");
        result("aaa", "ERROR");
        assertTrue(detector.sameCommitSignal(testId));
    }

    @Test
    void skippedIsIgnored() {                      // #5
        result("aaa", "PASSED");
        result("aaa", "SKIPPED");
        assertFalse(detector.sameCommitSignal(testId));
    }

    @Test
    void singleRunCannotFlip() {                   // #6
        result("aaa", "FAILED");
        assertFalse(detector.sameCommitSignal(testId));
    }

    // ---------- Signal D: the query's window rules ----------

    @Test
    void windowKeepsOnlyNewest30() {
        for (int i = 0; i < 10; i++) result("aaa", "FAILED");   // old
        for (int i = 0; i < 30; i++) result("aaa", "PASSED");   // newest 30
        FlakyDetector.FailureWindow window = detector.failureWindow(testId);
        assertEquals(30, window.runs());
        assertEquals(0, window.fails());
    }

    @Test
    void otherBranchesAreIgnored() {
        for (int i = 0; i < 12; i++) resultIn(run("aaa", "feature/x", 1), "FAILED");
        for (int i = 0; i < 12; i++) result("aaa", "PASSED");
        FlakyDetector.FailureWindow window = detector.failureWindow(testId);
        assertEquals(12, window.runs());
        assertEquals(0, window.fails());
    }

    @Test
    void brokenBuildsAreIgnored() {
        resultIn(run("aaa", "main", 5), "FAILED");               // 5 of 10 tests failed = 50%
        for (int i = 0; i < 10; i++) result("aaa", "PASSED");
        FlakyDetector.FailureWindow window = detector.failureWindow(testId);
        assertEquals(10, window.runs());
        assertEquals(0, window.fails());
    }
}