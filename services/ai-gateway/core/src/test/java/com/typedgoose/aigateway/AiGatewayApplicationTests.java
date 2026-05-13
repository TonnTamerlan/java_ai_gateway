package com.typedgoose.aigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.endpoints.web.exposure.include=health",
        "spring.ai.openai.api-key=test"
})
class AiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
