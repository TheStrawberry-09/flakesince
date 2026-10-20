package com.thanawat.flakesince.run;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CiRunRepository extends JpaRepository<CiRun, Long> {

    boolean existsByProjectIdAndProviderRunIdAndRunAttempt(Long projectId, String providerRunId, int runAttempt);
}