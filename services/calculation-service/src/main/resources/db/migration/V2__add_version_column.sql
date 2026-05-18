ALTER TABLE calc.summarization_jobs  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE calc.summarization_files ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
