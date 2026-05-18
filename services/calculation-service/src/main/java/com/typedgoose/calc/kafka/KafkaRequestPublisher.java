package com.typedgoose.calc.kafka;

import com.typedgoose.calc.domain.RequestPublisher;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.contracts.summarization.SummarizationRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaRequestPublisher implements RequestPublisher {

    private final KafkaTemplate<String, SummarizationRequestMessage> kafkaTemplate;

    @Override
    public void publish(SummarizationFile file) {
        SummarizationRequestMessage message = new SummarizationRequestMessage(
                file.jobId(),
                file.id(),
                file.correlationId(),
                null,
                file.instruction(),
                file.originalText());
        kafkaTemplate.send(KafkaConfig.SUMMARIZATION_REQUESTS_TOPIC,
                file.correlationId().toString(),
                message);
        log.info("published summarization request correlationId={} jobId={}",
                file.correlationId(), file.jobId());
    }
}
