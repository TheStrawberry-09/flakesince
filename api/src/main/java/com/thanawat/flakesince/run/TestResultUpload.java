package com.thanawat.flakesince.run;

import com.thanawat.flakesince.testcase.TestStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestResultUpload(
        @NotBlank @Size(max = 500) String suite,
        @NotBlank @Size(max = 500) String name,
        @NotNull TestStatus status,
        @Min(0) int durationMs,
        @Size(max = 10_000) String failureMessage
) {
}