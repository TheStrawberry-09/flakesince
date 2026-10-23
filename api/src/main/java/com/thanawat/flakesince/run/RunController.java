package com.thanawat.flakesince.run;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Runs", description = "Temporary JSON upload; replaced by Go ingestion in week 2")
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunIngestService ingestService;

    public RunController(RunIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Operation(summary = "Upload a CI run with its test results (temporary)")
    @PostMapping
    public ResponseEntity<IngestResult> upload(@Valid @RequestBody RunUploadRequest request) {
        IngestResult result = ingestService.ingest(request);
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }
}