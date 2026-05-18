package com.typedgoose.calc.api;

import java.util.List;
import java.util.UUID;

public record SummarizeResponse(
        UUID jobId,
        List<UUID> correlationIds) {
}
