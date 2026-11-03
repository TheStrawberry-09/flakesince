CREATE TABLE flaky_status (
                              test_case_id         BIGINT           PRIMARY KEY REFERENCES test_case (id) ON DELETE CASCADE,
                              project_id           BIGINT           NOT NULL REFERENCES project (id) ON DELETE CASCADE,
                              flaky                BOOLEAN          NOT NULL DEFAULT false,
                              score                DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (score BETWEEN 0 AND 1),
                              signal_same_commit   BOOLEAN          NOT NULL DEFAULT false,
                              signal_failure_rate  BOOLEAN          NOT NULL DEFAULT false,
                              failure_rate         DOUBLE PRECISION,
                              runs_checked         INT              NOT NULL DEFAULT 0,
                              first_detected_at    TIMESTAMPTZ,
                              last_seen_at         TIMESTAMPTZ,
                              updated_at           TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_flaky_status_project ON flaky_status (project_id, flaky);