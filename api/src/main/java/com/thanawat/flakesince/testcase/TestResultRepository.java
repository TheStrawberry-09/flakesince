package com.thanawat.flakesince.testcase;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    @EntityGraph(attributePaths = "ciRun")
    List<TestResult> findByTestCaseIdOrderByCiRunStartedAtAsc(Long testCaseId);

    @EntityGraph(attributePaths = "ciRun")
    Optional<TestResult> findFirstByTestCaseIdOrderByCiRunStartedAtDesc(Long testCaseId);
}