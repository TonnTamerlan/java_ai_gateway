package com.typedgoose.aigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.boot.admin.client.enabled=false",
        "management.endpoints.web.exposure.include=health"
})
class AiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
