CREATE SCHEMA IF NOT EXISTS calc;

CREATE TABLE calc.summarization_jobs (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    status      TEXT NOT NULL,
    file_count  INT NOT NULL
);

CREATE TABLE calc.summarization_files (
    id                UUID PRIMARY KEY,
    job_id            UUID NOT NULL REFERENCES calc.summarization_jobs(id),
    correlation_id    UUID NOT NULL UNIQUE,
    original_text     TEXT NOT NULL,
    instruction       TEXT NOT NULL,
    status            TEXT NOT NULL,
    summary           TEXT,
    model             TEXT,
    prompt_tokens     INT,
    completion_tokens INT,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_summarization_files_job_id ON calc.summarization_files(job_id);
CREATE INDEX idx_summarization_files_status ON calc.summarization_files(status);
