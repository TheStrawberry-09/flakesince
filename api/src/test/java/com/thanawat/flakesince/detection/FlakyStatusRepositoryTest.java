package com.thanawat.flakesince.detection;

import com.thanawat.flakesince.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FlakyStatusRepositoryTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FlakyStatusRepository repository;

    Long projectId;

    @BeforeEach
    void createProject() {
        projectId = jdbc.queryForObject(
                "INSERT INTO project (name, slug) VALUES ('Demo', 'status-demo') RETURNING id", Long.class);
    }

    Long createTestCase(String name) {
        return jdbc.queryForObject(
                "INSERT INTO test_case (project_id, suite, name) VALUES (?, 'DemoTests', ?) RETURNING id",
                Long.class, projectId, name);
    }

    @Test
    void findsOnlyFlakyTestsWorstFirst() {
        Instant now = Instant.parse("2026-11-01T10:00:00Z");

        FlakyStatus stable = new FlakyStatus(createTestCase("stable"), projectId);
        stable.record(false, false, 0.0, 20, 0.0, now);

        FlakyStatus littleFlaky = new FlakyStatus(createTestCase("littleFlaky"), projectId);
        littleFlaky.record(false, true, 0.1, 20, 0.4, now);

        FlakyStatus veryFlaky = new FlakyStatus(createTestCase("veryFlaky"), projectId);
        veryFlaky.record(true, true, 0.3, 20, 0.9, now);

        repository.saveAll(List.of(stable, littleFlaky, veryFlaky));

        List<FlakyStatus> flaky = repository.findByProjectIdAndFlakyTrueOrderByScoreDesc(projectId);

        assertEquals(2, flaky.size());
        assertEquals(veryFlaky.getTestCaseId(), flaky.get(0).getTestCaseId());
        assertEquals(littleFlaky.getTestCaseId(), flaky.get(1).getTestCaseId());
    }

    @Test
    void firstDetectedStaysWhileLastSeenMoves() {
        Instant first = Instant.parse("2026-11-01T10:00:00Z");
        Instant later = Instant.parse("2026-11-01T12:00:00Z");

        FlakyStatus status = new FlakyStatus(createTestCase("flaky"), projectId);
        status.record(true, false, null, 5, 1.0, first);
        status.record(true, false, null, 6, 1.0, later);

        assertEquals(first, status.getFirstDetectedAt());
        assertEquals(later, status.getLastSeenAt());
    }

    @Test
    void deletingTestCaseDeletesItsStatus() {
        Long testCaseId = createTestCase("gone");
        repository.saveAndFlush(new FlakyStatus(testCaseId, projectId));

        jdbc.update("DELETE FROM test_case WHERE id = ?", testCaseId);

        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM flaky_status WHERE test_case_id = ?", Long.class, testCaseId);
        assertEquals(0L, count);
    }

    @Test
    void datesStayRightWhenRunsArriveOutOfOrder() {
        Instant older = Instant.parse("2026-11-01T10:00:00Z");
        Instant newer = Instant.parse("2026-11-03T10:00:00Z");

        FlakyStatus status = new FlakyStatus(createTestCase("backfilled"), projectId);
        status.record(true, false, null, 5, 0.5, newer);   // backfill sends the newest first
        status.record(true, false, null, 6, 0.5, older);

        assertEquals(older, status.getFirstDetectedAt());
        assertEquals(newer, status.getLastSeenAt());
    }
}