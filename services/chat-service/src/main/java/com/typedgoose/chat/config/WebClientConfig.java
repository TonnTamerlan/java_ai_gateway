package com.typedgoose.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient aiGatewayClient(@LoadBalanced WebClient.Builder builder,
                                     @Value("${app.ai-gateway.base-url:lb://ai-gateway}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
