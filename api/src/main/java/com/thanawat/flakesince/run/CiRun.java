package com.thanawat.flakesince.run;

import com.thanawat.flakesince.project.Project;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ci_run")
public class CiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "provider_run_id", nullable = false)
    private String providerRunId;

    @Column(name = "run_attempt", nullable = false)
    private int runAttempt;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(nullable = false)
    private String branch;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int failed;

    protected CiRun() {
    }

    public CiRun(Project project, String providerRunId, int runAttempt, String commitSha,
                 String branch, Instant startedAt, int total, int failed) {
        this.project = project;
        this.providerRunId = providerRunId;
        this.runAttempt = runAttempt;
        this.commitSha = commitSha;
        this.branch = branch;
        this.startedAt = startedAt;
        this.total = total;
        this.failed = failed;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getProviderRunId() { return providerRunId; }
    public int getRunAttempt() { return runAttempt; }
    public String getCommitSha() { return commitSha; }
    public String getBranch() { return branch; }
    public Instant getStartedAt() { return startedAt; }
    public int getTotal() { return total; }
    public int getFailed() { return failed; }
}