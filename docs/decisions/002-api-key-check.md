# 002: The ingestion service checks API keys itself

## Status
Accepted, 2026-10-24

## Context
Every upload from the GitHub Action carries an API key in the
`Authorization: Bearer` header. The ingestion service (Go) must decide
whether to accept the upload and which project it belongs to.
The key hashes are stored in the `api_key` table, which the Spring API owns.

## Options considered
- **A. Go reads `api_key` directly.** Uploads keep working while Spring is
  down or busy, and bad keys are rejected at once. Cost: Go depends on the
  table's structure, and needs database credentials.
- **B. Go asks Spring through an internal endpoint.** Only Spring touches the
  database. Cost: if Spring is down, uploads fail, which removes most of the
  benefit of having a queue between them.
- **C. Spring checks the key when it reads the queue.** Simplest Go code.
  Cost: anyone can fill the queue, and uploads with a wrong key still get 202,
  so users never learn their setup is broken.

## Decision
Option A. Go looks up the SHA-256 hash of the key in `api_key`, using its own
read-only role (`ingest_reader`), and caches valid keys for 60 seconds.
Accepting uploads even when the main API is unavailable is the reason the
ingestion service and the queue exist, so the key check belongs in front of them.

## Consequences
- `ingest_reader` can only read three columns of `api_key`
  (`project_id`, `key_hash`, `revoked_at`). If its password leaked,
  nothing else would be exposed.
- Go depends on those three columns. Renaming them in a migration
  needs a matching change in Go.
- A revoked key can keep working for up to 60 seconds because of the cache.
- Only valid keys are cached, so random wrong keys cannot fill memory.
- Wrong or revoked keys get 401; a database problem gets 503 and is logged.