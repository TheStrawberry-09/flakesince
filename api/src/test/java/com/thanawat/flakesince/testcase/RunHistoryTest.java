package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.run.CiRun;
import com.thanawat.flakesince.run.CiRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RunHistoryTest {

    @Autowired ProjectRepository projects;
    @Autowired CiRunRepository runs;
    @Autowired TestCaseRepository testCases;
    @Autowired TestResultRepository results;

    @Test
    void historyIsOrderedOldestFirst() {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        TestCase flaky = testCases.save(new TestCase(project, "DemoTests", "flakyRandomTest()"));

        CiRun later = runs.save(new CiRun(project, "1001", 1, "abc123", "main",
                Instant.parse("2026-10-20T10:00:00Z"), 4, 0));
        CiRun earlier = runs.save(new CiRun(project, "1000", 1, "abc123", "main",
                Instant.parse("2026-10-20T09:00:00Z"), 4, 1));

        results.save(new TestResult(later, flaky, TestStatus.PASSED, 5, null));
        results.save(new TestResult(earlier, flaky, TestStatus.FAILED, 7, "expected: <true> but was: <false>"));

        List<TestResult> history = results.findByTestCaseIdOrderByCiRunStartedAtAsc(flaky.getId());

        assertEquals(2, history.size());
        assertEquals(TestStatus.FAILED, history.get(0).getStatus());
        assertEquals(TestStatus.PASSED, history.get(1).getStatus());
    }

    @Test
    void detectsAlreadySavedRunAttempt() {
        Project project = projects.save(new Project("Flaky Demo", "flaky-demo"));
        runs.save(new CiRun(project, "2000", 1, "def456", "main", Instant.now(), 4, 0));

        assertTrue(runs.existsByProjectIdAndProviderRunIdAndRunAttempt(project.getId(), "2000", 1));
        assertFalse(runs.existsByProjectIdAndProviderRunIdAndRunAttempt(project.getId(), "2000", 2));
    }
}