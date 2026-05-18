package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FilesRepository {

    private static final RowMapper<SummarizationFile> ROW_MAPPER = (rs, rowNum) -> new SummarizationFile(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("correlation_id", UUID.class),
            rs.getString("original_text"),
            rs.getString("instruction"),
            FileStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            rs.getString("model"),
            (Integer) rs.getObject("prompt_tokens"),
            (Integer) rs.getObject("completion_tokens"),
            rs.getString("error_message"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public void insert(SummarizationFile file) {
        jdbc.update(
                "INSERT INTO calc.summarization_files(" +
                        "id, job_id, correlation_id, original_text, instruction, status, " +
                        "summary, model, prompt_tokens, completion_tokens, error_message, " +
                        "created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                file.id(),
                file.jobId(),
                file.correlationId(),
                file.originalText(),
                file.instruction(),
                file.status().name(),
                file.summary(),
                file.model(),
                file.promptTokens(),
                file.completionTokens(),
                file.errorMessage(),
                Timestamp.from(file.createdAt()),
                Timestamp.from(file.updatedAt()));
    }

    public Optional<SummarizationFile> findByCorrelationId(UUID correlationId) {
        return jdbc.query(
                "SELECT * FROM calc.summarization_files WHERE correlation_id = ?",
                ROW_MAPPER,
                correlationId).stream().findFirst();
    }

    public List<SummarizationFile> findByJobId(UUID jobId) {
        return jdbc.query(
                "SELECT * FROM calc.summarization_files WHERE job_id = ? ORDER BY created_at",
                ROW_MAPPER,
                jobId);
    }

    /**
     * Idempotent terminal-state transition. Only updates rows still in PENDING — replays are no-ops.
     */
    public int markDone(UUID correlationId,
                        String summary,
                        String model,
                        Integer promptTokens,
                        Integer completionTokens,
                        Instant now) {
        return jdbc.update(
                "UPDATE calc.summarization_files " +
                        "SET status = 'DONE', summary = ?, model = ?, " +
                        "    prompt_tokens = ?, completion_tokens = ?, updated_at = ? " +
                        "WHERE correlation_id = ? AND status = 'PENDING'",
                summary,
                model,
                promptTokens,
                completionTokens,
                Timestamp.from(now),
                correlationId);
    }

    public int markFailed(UUID correlationId, String errorMessage, Instant now) {
        return jdbc.update(
                "UPDATE calc.summarization_files " +
                        "SET status = 'FAILED', error_message = ?, updated_at = ? " +
                        "WHERE correlation_id = ? AND status = 'PENDING'",
                errorMessage,
                Timestamp.from(now),
                correlationId);
    }
}
