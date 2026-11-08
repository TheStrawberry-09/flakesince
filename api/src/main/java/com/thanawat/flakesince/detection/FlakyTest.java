package com.thanawat.flakesince.detection;

import com.thanawat.flakesince.testcase.TestCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record FlakyTest(
        Long testCaseId,
        String suite,
        String name,
        double score,
        List<String> signals,
        Double failureRate,
        int runsChecked,
        Instant firstDetectedAt,
        Instant lastSeenAt
) {

    static FlakyTest from(FlakyStatus status, TestCase testCase) {
        List<String> signals = new ArrayList<>();
        if (status.isSignalSameCommit()) {
            signals.add("SAME_COMMIT");
        }
        if (status.isSignalFailureRate()) {
            signals.add("FAILURE_RATE");
        }
        return new FlakyTest(status.getTestCaseId(), testCase.getSuite(), testCase.getName(),
                status.getScore(), signals, status.getFailureRate(), status.getRunsChecked(),
                status.getFirstDetectedAt(), status.getLastSeenAt());
    }
}