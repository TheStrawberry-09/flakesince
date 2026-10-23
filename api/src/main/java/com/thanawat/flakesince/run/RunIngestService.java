package com.thanawat.flakesince.run;

import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.testcase.TestCase;
import com.thanawat.flakesince.testcase.TestCaseRepository;
import com.thanawat.flakesince.testcase.TestResult;
import com.thanawat.flakesince.testcase.TestResultRepository;
import com.thanawat.flakesince.testcase.TestStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class RunIngestService {

    private final ProjectRepository projects;
    private final CiRunRepository runs;
    private final TestCaseRepository testCases;
    private final TestResultRepository results;

    public RunIngestService(ProjectRepository projects, CiRunRepository runs,
                            TestCaseRepository testCases, TestResultRepository results) {
        this.projects = projects;
        this.runs = runs;
        this.testCases = testCases;
        this.results = results;
    }

    @Transactional
    public IngestResult ingest(RunUploadRequest upload) {
        Project project = projects.findById(upload.projectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project " + upload.projectId() + " not found"));

        Optional<CiRun> existing = runs.findByProjectIdAndProviderRunIdAndRunAttempt(
                project.getId(), upload.providerRunId(), upload.runAttempt());
        if (existing.isPresent()) {
            return new IngestResult(existing.get().getId(), true, 0);
        }

        int failed = (int) upload.results().stream()
                .filter(r -> r.status() == TestStatus.FAILED || r.status() == TestStatus.ERROR)
                .count();

        CiRun run = runs.save(new CiRun(project, upload.providerRunId(), upload.runAttempt(),
                upload.commitSha(), upload.branch(), upload.startedAt(), upload.results().size(), failed));

        for (TestResultUpload r : upload.results()) {
            TestCase testCase = testCases.findByProjectIdAndSuiteAndName(project.getId(), r.suite(), r.name())
                    .orElseGet(() -> testCases.save(new TestCase(project, r.suite(), r.name())));
            results.save(new TestResult(run, testCase, r.status(), r.durationMs(), r.failureMessage()));
        }

        return new IngestResult(run.getId(), false, upload.results().size());
    }
}