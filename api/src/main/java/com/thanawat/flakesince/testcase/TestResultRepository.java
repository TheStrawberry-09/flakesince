package com.thanawat.flakesince.testcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByTestCaseIdOrderByCiRunStartedAtAsc(Long testCaseId);
}