package com.typedgoose.aigateway.openai;

import com.typedgoose.contracts.ai.ModelTier;

@FunctionalInterface
public interface ModelTierMapper {

    String resolve(ModelTier tier);
}
