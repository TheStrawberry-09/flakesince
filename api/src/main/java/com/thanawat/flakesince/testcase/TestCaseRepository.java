package com.thanawat.flakesince.testcase;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    Optional<TestCase> findByProjectIdAndSuiteAndName(Long projectId, String suite, String name);

    Page<TestCase> findByProjectIdOrderBySuiteAscNameAsc(Long projectId, Pageable pageable);
}