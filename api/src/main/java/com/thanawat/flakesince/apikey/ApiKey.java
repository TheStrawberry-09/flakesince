package com.thanawat.flakesince.apikey;

import com.thanawat.flakesince.project.Project;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(Project project, String keyHash, String label) {
        this.project = project;
        this.keyHash = keyHash;
        this.label = label;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getKeyHash() { return keyHash; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}