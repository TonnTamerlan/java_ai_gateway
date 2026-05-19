package com.typedgoose.aigateway.summarization;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BatchCorrelationsRepository extends CrudRepository<BatchCorrelation, UUID> {

    @Query("SELECT DISTINCT provider_batch_id FROM ai.batch_correlations " +
            "WHERE status IN ('PENDING', 'IN_PROGRESS')")
    List<String> findOpenProviderBatchIds();

    List<BatchCorrelation> findByProviderBatchId(String providerBatchId);

    @Modifying
    @Query("UPDATE ai.batch_correlations " +
            "SET status = 'DONE', completed_at = :now, version = version + 1 " +
            "WHERE correlation_id = :correlationId " +
            "AND status IN ('PENDING', 'IN_PROGRESS')")
    int markDone(UUID correlationId, Instant now);

    @Modifying
    @Query("UPDATE ai.batch_correlations " +
            "SET status = 'FAILED', completed_at = :now, version = version + 1 " +
            "WHERE correlation_id = :correlationId " +
            "AND status IN ('PENDING', 'IN_PROGRESS')")
    int markFailed(UUID correlationId, Instant now);
}
