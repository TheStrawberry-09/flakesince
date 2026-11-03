package com.thanawat.flakesince.detection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlakyStatusRepository extends JpaRepository<FlakyStatus, Long> {

    List<FlakyStatus> findByProjectIdAndFlakyTrueOrderByScoreDesc(Long projectId);
}