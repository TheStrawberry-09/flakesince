package com.thanawat.flakesince.apikey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "API keys", description = "Upload keys for CI")
@RestController
@RequestMapping("/api/projects/{projectId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Operation(summary = "Create an API key (the key is shown only once)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse create(@PathVariable Long projectId,
                                        @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.create(projectId, request.label());
    }
}