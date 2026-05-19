package com.typedgoose.contracts.ai;

import java.util.List;
import java.util.UUID;

public record BatchSubmission(List<BatchItem> items) {

    public record BatchItem(
            UUID correlationId,
            ModelTier modelTier,
            String instruction,
            String content) {
    }
}
