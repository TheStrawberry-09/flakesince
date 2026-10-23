package com.thanawat.flakesince.run;

public record IngestResult(Long runId, boolean duplicate, int resultsSaved) {
}