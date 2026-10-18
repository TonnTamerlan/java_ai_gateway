package com.typedgoose.calc.domain;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Builder
@Table(schema = "calc", name = "summarization_files")
public record SummarizationFile(
        @Id UUID id,
        @Version Long version,
        UUID jobId,
        UUID correlationId,
        String fileName,
        String originalText,
        String instruction,
        FileStatus status,
        String summary,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
