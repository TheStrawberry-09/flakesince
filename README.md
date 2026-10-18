# FlakeSince

Find flaky tests in CI and the commit they started at. Free and self-hosted.

FlakeSince reads the JUnit XML your CI already produces, keeps a pass/fail history per commit, and estimates when each test became flaky, with a confidence level.

**Status:** early development. First release (v0.1.0) planned for November 2026.

**Stack:** Spring Boot 4.1 (Java 25), Go, PostgreSQL, Amazon SQS, Angular, AWS. Licensed under Apache-2.0.