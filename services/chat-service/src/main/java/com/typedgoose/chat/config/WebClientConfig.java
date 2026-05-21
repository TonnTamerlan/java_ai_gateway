package com.typedgoose.chat.config;

import com.typedgoose.chat.client.AiGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public AiGatewayClient aiGatewayClient(@LoadBalanced WebClient.Builder builder,
                                           @Value("${app.ai-gateway.base-url:lb://ai-gateway}") String baseUrl) {
        WebClient webClient = builder.baseUrl(baseUrl).build();
        return HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build()
                .createClient(AiGatewayClient.class);
    }
}
