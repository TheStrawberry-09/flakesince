package com.thanawat.flakesince.testcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    Optional<TestCase> findByProjectIdAndSuiteAndName(Long projectId, String suite, String name);
}