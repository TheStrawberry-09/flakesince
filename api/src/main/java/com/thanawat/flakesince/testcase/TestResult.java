package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.run.CiRun;
import jakarta.persistence.*;

@Entity
@Table(name = "test_result")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ci_run_id", nullable = false)
    private CiRun ciRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "failure_message")
    private String failureMessage;

    protected TestResult() {
    }

    public TestResult(CiRun ciRun, TestCase testCase, TestStatus status, int durationMs, String failureMessage) {
        this.ciRun = ciRun;
        this.testCase = testCase;
        this.status = status;
        this.durationMs = durationMs;
        this.failureMessage = failureMessage;
    }

    public Long getId() { return id; }
    public CiRun getCiRun() { return ciRun; }
    public TestCase getTestCase() { return testCase; }
    public TestStatus getStatus() { return status; }
    public int getDurationMs() { return durationMs; }
    public String getFailureMessage() { return failureMessage; }
}