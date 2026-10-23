package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.common.PageResponse;
import com.thanawat.flakesince.project.ProjectRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TestHistoryService {

    private final ProjectRepository projects;
    private final TestCaseRepository testCases;
    private final TestResultRepository results;

    public TestHistoryService(ProjectRepository projects, TestCaseRepository testCases,
                              TestResultRepository results) {
        this.projects = projects;
        this.testCases = testCases;
        this.results = results;
    }

    public List<HistoryEntry> history(Long testCaseId) {
        if (!testCases.existsById(testCaseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test " + testCaseId + " not found");
        }
        return results.findByTestCaseIdOrderByCiRunStartedAtAsc(testCaseId).stream()
                .map(HistoryEntry::from)
                .toList();
    }

    public PageResponse<TestSummary> tests(Long projectId, Pageable pageable) {
        if (!projects.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project " + projectId + " not found");
        }
        return PageResponse.from(
                testCases.findByProjectIdOrderBySuiteAscNameAsc(projectId, pageable).map(this::summarize));
    }

    private TestSummary summarize(TestCase testCase) {
        Optional<TestResult> latest = results.findFirstByTestCaseIdOrderByCiRunStartedAtDesc(testCase.getId());
        return new TestSummary(testCase.getId(), testCase.getSuite(), testCase.getName(),
                latest.map(TestResult::getStatus).orElse(null),
                latest.map(r -> r.getCiRun().getStartedAt()).orElse(null));
    }
}