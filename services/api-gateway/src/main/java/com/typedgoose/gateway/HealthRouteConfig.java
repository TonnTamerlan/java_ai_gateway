package com.typedgoose.gateway;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class HealthRouteConfig {

    @Bean
    public RouterFunction<ServerResponse> healthRoute() {
        return RouterFunctions.route()
                .GET("/api/health", req -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("status", "UP")))
                .build();
    }
}
