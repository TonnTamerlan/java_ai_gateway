package com.typedgoose.calc.domain;

import java.time.Instant;
import java.util.UUID;

public record SummarizationFile(
        UUID id,
        UUID jobId,
        UUID correlationId,
        String originalText,
        String instruction,
        FileStatus status,
        String summary,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}
