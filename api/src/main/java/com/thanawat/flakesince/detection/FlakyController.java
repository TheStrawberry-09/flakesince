package com.thanawat.flakesince.detection;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flaky tests", description = "Tests FlakeSince has flagged as flaky")
@RestController
@RequestMapping("/api")
public class FlakyController {

    private final FlakyService service;

    public FlakyController(FlakyService service) {
        this.service = service;
    }

    @Operation(summary = "A project's flaky tests, most certain and worst first")
    @GetMapping("/projects/{projectId}/flaky")
    public List<FlakyTest> flaky(@PathVariable Long projectId) {
        return service.flakyTests(projectId);
    }
}