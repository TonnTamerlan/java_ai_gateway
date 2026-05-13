package com.typedgoose.aigateway.config;

import com.typedgoose.aigateway.openai.ModelTierMapper;
import com.typedgoose.aigateway.openai.OpenAiChatProvider;
import com.typedgoose.contracts.ai.ChatProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelTierProperties.class)
class AiGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai", matchIfMissing = true)
    ChatProvider openAiChatProvider(ChatModel chatModel, ModelTierMapper modelTierMapper) {
        return new OpenAiChatProvider(chatModel, modelTierMapper);
    }
}
