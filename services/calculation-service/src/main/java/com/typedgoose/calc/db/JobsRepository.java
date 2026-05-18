package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.SummarizationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JobsRepository {

    private static final RowMapper<SummarizationJob> ROW_MAPPER = (rs, rowNum) -> new SummarizationJob(
            rs.getObject("id", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("file_count"));

    private final JdbcTemplate jdbc;

    public void insert(SummarizationJob job) {
        jdbc.update(
                "INSERT INTO calc.summarization_jobs(id, created_at, updated_at, status, file_count) " +
                        "VALUES (?, ?, ?, ?, ?)",
                job.id(),
                Timestamp.from(job.createdAt()),
                Timestamp.from(job.updatedAt()),
                job.status().name(),
                job.fileCount());
    }

    public Optional<SummarizationJob> findById(UUID id) {
        return jdbc.query(
                "SELECT id, created_at, updated_at, status, file_count FROM calc.summarization_jobs WHERE id = ?",
                ROW_MAPPER,
                id).stream().findFirst();
    }

    public void updateStatus(UUID id, JobStatus status, Instant now) {
        jdbc.update(
                "UPDATE calc.summarization_jobs SET status = ?, updated_at = ? WHERE id = ?",
                status.name(),
                Timestamp.from(now),
                id);
    }
}
