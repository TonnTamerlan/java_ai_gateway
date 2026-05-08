package com.typedgoose.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "management.endpoints.web.exposure.include=health"
})
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
