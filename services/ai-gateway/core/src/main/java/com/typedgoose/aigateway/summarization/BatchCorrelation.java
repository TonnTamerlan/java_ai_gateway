package com.typedgoose.aigateway.summarization;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(schema = "ai", name = "batch_correlations")
public record BatchCorrelation(
        @Id UUID correlationId,
        @Version Long version,
        UUID jobId,
        UUID fileId,
        String providerBatchId,
        String instruction,
        String content,
        BatchCorrelationStatus status,
        Instant submittedAt,
        Instant completedAt) {
}
