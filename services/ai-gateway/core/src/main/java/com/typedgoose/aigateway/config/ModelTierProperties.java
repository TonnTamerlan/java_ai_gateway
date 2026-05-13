package com.typedgoose.aigateway.config;

import com.typedgoose.aigateway.openai.ModelTierMapper;
import com.typedgoose.contracts.ai.ModelTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.models")
public record ModelTierProperties(String fast, String medium, String slow) implements ModelTierMapper {

    @Override
    public String resolve(ModelTier tier) {
        return switch (tier) {
            case FAST -> fast;
            case MEDIUM -> medium;
            case SLOW -> slow;
        };
    }
}
