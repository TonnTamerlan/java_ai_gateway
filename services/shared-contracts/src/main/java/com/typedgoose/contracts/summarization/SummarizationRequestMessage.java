package com.typedgoose.contracts.summarization;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SummarizationRequestMessage(
        UUID jobId,
        UUID fileId,
        UUID correlationId,
        String filename,
        String instruction,
        String content) {
}
