package com.typedgoose.aigateway.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.typedgoose.aigateway.openai.ModelTierMapper;
import com.typedgoose.aigateway.openai.OpenAiBatchProvider;
import com.typedgoose.aigateway.openai.OpenAiChatProvider;
import com.typedgoose.contracts.ai.BatchProvider;
import com.typedgoose.contracts.ai.ChatProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ModelTierProperties.class)
class AiGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai", matchIfMissing = true)
    ChatProvider openAiChatProvider(ChatModel chatModel, ModelTierMapper modelTierMapper) {
        return new OpenAiChatProvider(chatModel, modelTierMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai", matchIfMissing = true)
    OpenAIClient openAiClient(@Value("${spring.ai.openai.api-key}") String apiKey) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai", matchIfMissing = true)
    BatchProvider openAiBatchProvider(OpenAIClient client,
                                      ModelTierMapper modelTierMapper,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        return new OpenAiBatchProvider(client, modelTierMapper, objectMapper, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
