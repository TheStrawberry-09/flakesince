package com.thanawat.flakesince.run;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record RunUploadRequest(
        @NotNull Long projectId,
        @NotBlank @Size(max = 64) String commitSha,
        @NotBlank @Size(max = 255) String branch,
        @NotBlank @Size(max = 100) String providerRunId,
        @Min(1) int runAttempt,
        @NotNull Instant startedAt,
        @NotEmpty @Size(max = 10_000) List<@Valid TestResultUpload> results
) {
}