package com.typedgoose.calc.domain;

import java.time.Instant;
import java.util.UUID;

public record SummarizationJob(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        JobStatus status,
        int fileCount) {
}
