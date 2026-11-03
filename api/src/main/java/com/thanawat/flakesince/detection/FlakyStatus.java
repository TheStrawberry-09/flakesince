package com.thanawat.flakesince.detection;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "flaky_status")
public class FlakyStatus {

    @Id
    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private boolean flaky;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private boolean signalSameCommit;

    @Column(nullable = false)
    private boolean signalFailureRate;

    private Double failureRate;

    @Column(nullable = false)
    private int runsChecked;

    private Instant firstDetectedAt;

    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected FlakyStatus() {
    }

    public FlakyStatus(Long testCaseId, Long projectId) {
        this.testCaseId = testCaseId;
        this.projectId = projectId;
        this.updatedAt = Instant.now();
    }

    public void record(boolean sameCommit, boolean failureRateHit, Double failureRate,
                       int runsChecked, double score, Instant now) {
        this.signalSameCommit = sameCommit;
        this.signalFailureRate = failureRateHit;
        this.failureRate = failureRate;
        this.runsChecked = runsChecked;
        this.score = score;
        this.flaky = sameCommit || failureRateHit;
        if (flaky) {
            if (firstDetectedAt == null) {
                firstDetectedAt = now;
            }
            lastSeenAt = now;
        }
        this.updatedAt = now;
    }

    public Long getTestCaseId() { return testCaseId; }
    public Long getProjectId() { return projectId; }
    public boolean isFlaky() { return flaky; }
    public double getScore() { return score; }
    public boolean isSignalSameCommit() { return signalSameCommit; }
    public boolean isSignalFailureRate() { return signalFailureRate; }
    public Double getFailureRate() { return failureRate; }
    public int getRunsChecked() { return runsChecked; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}