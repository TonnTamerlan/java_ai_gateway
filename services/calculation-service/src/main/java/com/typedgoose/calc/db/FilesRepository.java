package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.SummarizationFile;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilesRepository extends CrudRepository<SummarizationFile, UUID> {

    Optional<SummarizationFile> findByCorrelationId(UUID correlationId);

    List<SummarizationFile> findByJobIdOrderByCreatedAt(UUID jobId);

    @Modifying
    @Query("UPDATE calc.summarization_files " +
            "SET status = 'DONE', summary = :summary, model = :model, " +
            "    prompt_tokens = :promptTokens, completion_tokens = :completionTokens, " +
            "    updated_at = :now, version = version + 1 " +
            "WHERE correlation_id = :correlationId AND status = 'PENDING'")
    int markDone(UUID correlationId,
                 String summary,
                 String model,
                 Integer promptTokens,
                 Integer completionTokens,
                 Instant now);

    @Modifying
    @Query("UPDATE calc.summarization_files " +
            "SET status = 'FAILED', error_message = :errorMessage, " +
            "    updated_at = :now, version = version + 1 " +
            "WHERE correlation_id = :correlationId AND status = 'PENDING'")
    int markFailed(UUID correlationId, String errorMessage, Instant now);
}
