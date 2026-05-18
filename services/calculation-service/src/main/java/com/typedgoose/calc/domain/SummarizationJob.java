package com.typedgoose.calc.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(schema = "calc", name = "summarization_jobs")
public record SummarizationJob(
        @Id UUID id,
        @Version Long version,
        Instant createdAt,
        Instant updatedAt,
        JobStatus status,
        int fileCount) {
}
