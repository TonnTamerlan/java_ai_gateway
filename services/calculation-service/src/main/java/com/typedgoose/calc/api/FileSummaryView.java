package com.typedgoose.calc.api;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
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
        return FileSummaryView.builder()
                .id(f.id())
                .jobId(f.jobId())
                .correlationId(f.correlationId())
                .fileName(f.fileName())
                .status(f.status())
                .instruction(f.instruction())
                .summary(f.summary())
                .model(f.model())
                .promptTokens(f.promptTokens())
                .completionTokens(f.completionTokens())
                .errorMessage(f.errorMessage())
                .createdAt(f.createdAt())
                .updatedAt(f.updatedAt())
                .build();
    }
}
