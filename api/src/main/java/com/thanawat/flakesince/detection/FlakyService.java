package com.thanawat.flakesince.detection;

import com.thanawat.flakesince.project.ProjectRepository;
import com.thanawat.flakesince.testcase.TestCase;
import com.thanawat.flakesince.testcase.TestCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FlakyService {

    private final ProjectRepository projects;
    private final FlakyStatusRepository statuses;
    private final TestCaseRepository testCases;

    public FlakyService(ProjectRepository projects, FlakyStatusRepository statuses,
                        TestCaseRepository testCases) {
        this.projects = projects;
        this.statuses = statuses;
        this.testCases = testCases;
    }

    public List<FlakyTest> flakyTests(Long projectId) {
        if (!projects.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project " + projectId + " not found");
        }

        List<FlakyStatus> flaky = statuses.findByProjectIdAndFlakyTrueOrderByScoreDesc(projectId);

        Map<Long, TestCase> cases = testCases
                .findAllById(flaky.stream().map(FlakyStatus::getTestCaseId).toList())
                .stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity()));

        return flaky.stream()
                .map(status -> FlakyTest.from(status, cases.get(status.getTestCaseId())))
                .sorted(Comparator.comparingDouble(FlakyTest::score).reversed()
                        .thenComparing(FlakyTest::failureRate,
                                Comparator.nullsLast(Comparator.<Double>reverseOrder())))
                .toList();
    }
}