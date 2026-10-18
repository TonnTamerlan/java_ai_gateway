package com.typedgoose.contracts.summarization;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SummarizationResponseMessage(
        UUID jobId,
        UUID fileId,
        UUID correlationId,
        Status status,
        String summary,
        String model,
        int promptTokens,
        int completionTokens,
        String errorMessage) {

    public enum Status {
        DONE,
        FAILED
    }
}
