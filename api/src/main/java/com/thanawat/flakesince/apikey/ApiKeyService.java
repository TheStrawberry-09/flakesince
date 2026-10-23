package com.thanawat.flakesince.apikey;

import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ApiKeyService {

    static final String PREFIX = "fs_live_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeys;
    private final ProjectRepository projects;

    public ApiKeyService(ApiKeyRepository apiKeys, ProjectRepository projects) {
        this.apiKeys = apiKeys;
        this.projects = projects;
    }

    @Transactional
    public ApiKeyCreatedResponse create(Long projectId, String label) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project " + projectId + " not found"));

        String rawKey = generateKey();
        ApiKey saved = apiKeys.save(new ApiKey(project, hash(rawKey), label));

        return new ApiKeyCreatedResponse(saved.getId(), saved.getLabel(), rawKey, saved.getCreatedAt());
    }

    static String generateKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @Transactional
    public void revoke(Long keyId) {
        ApiKey key = apiKeys.findById(keyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "API key " + keyId + " not found"));
        key.revoke();
    }
}