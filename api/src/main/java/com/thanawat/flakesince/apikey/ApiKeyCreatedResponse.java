package com.thanawat.flakesince.apikey;

import java.time.Instant;

public record ApiKeyCreatedResponse(Long id, String label, String key, Instant createdAt) {
}