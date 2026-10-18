package com.typedgoose.contracts.ai;

import lombok.Builder;

import java.util.Optional;
import java.util.UUID;

@Builder
public record BatchResult(
        UUID correlationId,
        String summary,
        String model,
        int promptTokens,
        int completionTokens,
        Optional<String> error) {
}
