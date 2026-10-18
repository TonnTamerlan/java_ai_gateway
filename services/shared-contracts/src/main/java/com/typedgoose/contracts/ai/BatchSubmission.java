package com.typedgoose.contracts.ai;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

public record BatchSubmission(List<BatchItem> items) {

    @Builder
    public record BatchItem(
            UUID correlationId,
            ModelTier modelTier,
            String instruction,
            String content) {
    }
}
