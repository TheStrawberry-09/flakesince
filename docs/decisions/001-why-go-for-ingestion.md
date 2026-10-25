# 001: A separate ingestion service in Go

## Status
Accepted, 2026-10-25

## Context
Every CI run uploads a JUnit XML file, up to 10 MB. Uploads arrive in bursts
(many repositories finish CI at the same time) and must not be lost when the
main API is busy, restarting or down. The main API is a Spring Boot app that
owns the database and will run the detection logic.

## Options considered
- **A. Spring Boot receives uploads directly.** One service, less to deploy.
  Cost: uploads fail whenever the API is down or overloaded, and parsing large
  files competes with the API's own work.
- **B. A separate Go service in front, with a queue to Spring.** Uploads are
  accepted even when the API is down; each part scales on its own.
  Cost: two services, a queue and a message format to keep in sync.
- **C. A separate service in Java/Spring.** Same benefits as B, one language.
  Cost: a heavier runtime for a small, single-purpose service.

## Decision
Option B. Accepting uploads must keep working when the API does not, which
needs a separate service and a queue. Go fits a small network service well:
it starts in milliseconds, uses little memory, builds to a single binary and
its standard library includes a production-ready HTTP server and XML parser.

## Consequences
- The Go service and Spring share a message contract (`RunMessage` in Go,
  `RunUploadRequest` in Java); changing one means changing both.
- Spring must handle each message being delivered more than once; it saves a
  run attempt only once (unique run ID + attempt).
- Local development needs a queue: ElasticMQ, which speaks the SQS protocol.
- Two languages in one repository: two CI workflows, two dependency updaters.