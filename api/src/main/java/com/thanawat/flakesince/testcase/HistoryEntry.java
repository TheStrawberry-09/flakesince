package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.run.CiRun;

import java.time.Instant;

public record HistoryEntry(Long runId, String commitSha, String branch, int runAttempt,
                           Instant startedAt, TestStatus status, int durationMs, String failureMessage) {

    static HistoryEntry from(TestResult result) {
        CiRun run = result.getCiRun();
        return new HistoryEntry(run.getId(), run.getCommitSha(), run.getBranch(), run.getRunAttempt(),
                run.getStartedAt(), result.getStatus(), result.getDurationMs(), result.getFailureMessage());
    }
}