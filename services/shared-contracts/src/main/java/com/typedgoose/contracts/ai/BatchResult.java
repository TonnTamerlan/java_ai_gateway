package com.typedgoose.contracts.ai;

import java.util.Optional;
import java.util.UUID;

public record BatchResult(
        UUID correlationId,
        String summary,
        String model,
        int promptTokens,
        int completionTokens,
        Optional<String> error) {
}
