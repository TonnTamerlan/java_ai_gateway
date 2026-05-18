package com.typedgoose.calc.config;

import com.typedgoose.calc.domain.RequestPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class NoOpRequestPublisher {

    @Bean
    @ConditionalOnMissingBean(RequestPublisher.class)
    public RequestPublisher defaultRequestPublisher() {
        return file -> log.info(
                "no-op publisher: would have published correlationId={} jobId={} (Step 08 wires Kafka)",
                file.correlationId(),
                file.jobId());
    }
}
