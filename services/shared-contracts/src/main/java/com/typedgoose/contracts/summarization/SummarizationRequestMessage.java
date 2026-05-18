package com.typedgoose.contracts.summarization;

import java.util.UUID;

public record SummarizationRequestMessage(
        UUID jobId,
        UUID fileId,
        UUID correlationId,
        String filename,
        String instruction,
        String content) {
}
