package com.typedgoose.contracts.ai;

import java.time.Instant;

public record BatchHandle(String providerBatchId, Instant submittedAt) {
}
