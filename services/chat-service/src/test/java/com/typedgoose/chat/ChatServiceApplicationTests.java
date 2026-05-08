package com.typedgoose.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.endpoints.web.exposure.include=health"
})
class ChatServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
