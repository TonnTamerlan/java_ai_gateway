CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE ai.batch_correlations (
    correlation_id     UUID PRIMARY KEY,
    job_id             UUID NOT NULL,
    file_id            UUID NOT NULL,
    provider_batch_id  TEXT NOT NULL,
    instruction        TEXT NOT NULL,
    content            TEXT NOT NULL,
    status             TEXT NOT NULL,
    submitted_at       TIMESTAMPTZ NOT NULL,
    completed_at       TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_batch_correlations_batch_id
    ON ai.batch_correlations (provider_batch_id);

CREATE INDEX idx_batch_correlations_open
    ON ai.batch_correlations (provider_batch_id)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
