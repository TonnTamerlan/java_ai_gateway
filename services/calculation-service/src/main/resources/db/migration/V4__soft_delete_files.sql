ALTER TABLE calc.summarization_files
    ADD COLUMN deleted_at TIMESTAMPTZ NULL;

CREATE INDEX idx_summ_files_active_created
    ON calc.summarization_files (created_at DESC)
    WHERE deleted_at IS NULL;
