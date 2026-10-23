package com.thanawat.flakesince.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CiRunRepository extends JpaRepository<CiRun, Long> {

    boolean existsByProjectIdAndProviderRunIdAndRunAttempt(Long projectId, String providerRunId, int runAttempt);
    Optional<CiRun> findByProjectIdAndProviderRunIdAndRunAttempt(Long projectId, String providerRunId, int runAttempt);
}