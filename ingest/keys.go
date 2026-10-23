package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ErrInvalidKey means the key is unknown or has been revoked.
var ErrInvalidKey = errors.New("invalid or revoked API key")

// KeyChecker finds the project an API key belongs to.
type KeyChecker interface {
	Check(ctx context.Context, rawKey string) (int64, error)
}

// hashKey must produce the same value as ApiKeyService.hash in the Spring API.
func hashKey(rawKey string) string {
	sum := sha256.Sum256([]byte(rawKey))
	return hex.EncodeToString(sum[:])
}

type dbKeyChecker struct {
	pool *pgxpool.Pool
}

func (c *dbKeyChecker) Check(ctx context.Context, rawKey string) (int64, error) {
	var projectID int64
	var revokedAt *time.Time

	err := c.pool.QueryRow(ctx,
		"SELECT project_id, revoked_at FROM api_key WHERE key_hash = $1",
		hashKey(rawKey),
	).Scan(&projectID, &revokedAt)

	if errors.Is(err, pgx.ErrNoRows) {
		return 0, ErrInvalidKey
	}
	if err != nil {
		return 0, fmt.Errorf("checking API key: %w", err)
	}
	if revokedAt != nil {
		return 0, ErrInvalidKey
	}
	return projectID, nil
}

type cachedKey struct {
	projectID int64
	expires   time.Time
}

type cachingKeyChecker struct {
	next    KeyChecker
	ttl     time.Duration
	now     func() time.Time
	mu      sync.Mutex
	entries map[string]cachedKey
}

func newCachingKeyChecker(next KeyChecker, ttl time.Duration) *cachingKeyChecker {
	return &cachingKeyChecker{
		next:    next,
		ttl:     ttl,
		now:     time.Now,
		entries: map[string]cachedKey{},
	}
}

func (c *cachingKeyChecker) Check(ctx context.Context, rawKey string) (int64, error) {
	hash := hashKey(rawKey)

	c.mu.Lock()
	entry, found := c.entries[hash]
	c.mu.Unlock()
	if found && c.now().Before(entry.expires) {
		return entry.projectID, nil
	}

	projectID, err := c.next.Check(ctx, rawKey)
	if err != nil {
		c.mu.Lock()
		delete(c.entries, hash)
		c.mu.Unlock()
		return 0, err
	}

	c.mu.Lock()
	c.entries[hash] = cachedKey{projectID: projectID, expires: c.now().Add(c.ttl)}
	c.mu.Unlock()
	return projectID, nil
}