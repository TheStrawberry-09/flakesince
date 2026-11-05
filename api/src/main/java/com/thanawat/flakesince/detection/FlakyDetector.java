package com.thanawat.flakesince.detection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FlakyDetector {

    static final int WINDOW = 30;
    static final int MIN_RUNS = 10;
    private final JdbcTemplate jdbc;

    public FlakyDetector(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Signal A: some commit has both a pass and a fail for this test. */
    private static final String SAME_COMMIT_SQL = """
        select exists (
            select r.commit_sha
            from test_result tr
            join ci_run r on r.id = tr.ci_run_id
            where tr.test_case_id = ?
            group by r.commit_sha
            having count(*) filter (where tr.status = 'PASSED') > 0
               and count(*) filter (where tr.status in ('FAILED', 'ERROR')) > 0
        )
        """;

    private static final String FAILURE_WINDOW_SQL = """
        select count(*)                                              as runs,
               count(*) filter (where status in ('FAILED', 'ERROR')) as fails
        from (
            select tr.status
            from test_result tr
            join ci_run r on r.id = tr.ci_run_id
            where tr.test_case_id = ?
              and r.branch = 'main'
              and tr.status <> 'SKIPPED'
              and r.failed <= r.total * 0.3
            order by r.started_at desc
            limit ?
        ) as recent
        """;

    /** How many of the last runs on main failed. */
    public record FailureWindow(int runs, int fails) {

        /** null when there are too few runs to judge. */
        public Double rate() {
            return runs < MIN_RUNS ? null : (double) fails / runs;
        }
    }

    public FailureWindow failureWindow(long testCaseId) {
        return jdbc.queryForObject(FAILURE_WINDOW_SQL,
                (rs, rowNum) -> new FailureWindow(rs.getInt("runs"), rs.getInt("fails")),
                testCaseId, WINDOW);
    }

    /** Signal D: fails often enough lately to look flaky. */
    public boolean failureRateSignal(FailureWindow window) {
        // TODO (you): return false if there are fewer than MIN_RUNS runs,
        if (window.runs() < MIN_RUNS) {
            return false;
        }
        return window.fails() >= 2 && window.rate() < 0.9;
    }

    /** Signal A: some commit has both a pass and a fail for this test. */
    public boolean sameCommitSignal(long testCaseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(SAME_COMMIT_SQL, Boolean.class, testCaseId));
    }

}