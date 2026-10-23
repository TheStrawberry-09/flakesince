package com.thanawat.flakesince.testcase;

import java.time.Instant;

public record TestSummary(Long id, String suite, String name, TestStatus latestStatus, Instant lastRunAt) {
}