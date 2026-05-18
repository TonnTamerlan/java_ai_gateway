package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.SummarizationJob;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.UUID;

public interface JobsRepository extends CrudRepository<SummarizationJob, UUID> {

    @Modifying
    @Query("UPDATE calc.summarization_jobs " +
            "SET status = :status, updated_at = :now, version = version + 1 " +
            "WHERE id = :id")
    int updateStatus(UUID id, JobStatus status, Instant now);
}
