package com.typedgoose.calc.api;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;

import java.time.Instant;
import java.util.UUID;

public record FileSummaryView(
        UUID id,
        UUID jobId,
        UUID correlationId,
        String fileName,
        FileStatus status,
        String instruction,
        String summary,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static FileSummaryView of(SummarizationFile f) {
        return new FileSummaryView(
                f.id(), f.jobId(), f.correlationId(),
                f.fileName(), f.status(),
                f.instruction(), f.summary(),
                f.model(), f.promptTokens(), f.completionTokens(),
                f.errorMessage(),
                f.createdAt(), f.updatedAt());
    }
}
