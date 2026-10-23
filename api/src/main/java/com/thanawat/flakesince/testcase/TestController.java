package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tests", description = "Test lists and pass/fail history")
@RestController
@RequestMapping("/api")
public class TestController {

    private final TestHistoryService historyService;

    public TestController(TestHistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(summary = "List a project's tests with their latest status")
    @GetMapping("/projects/{projectId}/tests")
    public PageResponse<TestSummary> tests(@PathVariable Long projectId,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return historyService.tests(projectId, pageable);
    }

    @Operation(summary = "Pass/fail history of one test, oldest first")
    @GetMapping("/tests/{id}/history")
    public List<HistoryEntry> history(@PathVariable Long id) {
        return historyService.history(id);
    }
}