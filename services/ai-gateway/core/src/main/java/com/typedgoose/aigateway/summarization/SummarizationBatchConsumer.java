package com.typedgoose.aigateway.summarization;

import com.typedgoose.aigateway.kafka.KafkaConfig;
import com.typedgoose.contracts.ai.BatchHandle;
import com.typedgoose.contracts.ai.BatchProvider;
import com.typedgoose.contracts.ai.BatchSubmission;
import com.typedgoose.contracts.ai.ModelTier;
import com.typedgoose.contracts.summarization.SummarizationRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class SummarizationBatchConsumer {

    private final BatchProvider batchProvider;
    private final BatchCorrelationsRepository correlations;
    private final Clock clock;

    @KafkaListener(
            topics = KafkaConfig.SUMMARIZATION_REQUESTS_TOPIC,
            containerFactory = "batchListenerContainerFactory")
    @Transactional
    public void onBatch(List<SummarizationRequestMessage> messages) {
        if (messages.isEmpty()) return;

        List<BatchSubmission.BatchItem> items = new ArrayList<>(messages.size());
        for (SummarizationRequestMessage m : messages) {
            items.add(new BatchSubmission.BatchItem(
                    m.correlationId(),
                    ModelTier.FAST,
                    m.instruction(),
                    m.content()));
        }

        BatchHandle handle = batchProvider.submit(new BatchSubmission(items));
        Instant now = clock.instant();

        List<BatchCorrelation> rows = new ArrayList<>(messages.size());
        for (SummarizationRequestMessage m : messages) {
            rows.add(new BatchCorrelation(
                    m.correlationId(),
                    null,
                    m.jobId(),
                    m.fileId(),
                    handle.providerBatchId(),
                    m.instruction(),
                    m.content(),
                    BatchCorrelationStatus.IN_PROGRESS,
                    now,
                    null));
        }
        correlations.saveAll(rows);

        log.info("submitted batch to provider: batchId={} items={}",
                handle.providerBatchId(), rows.size());
    }
}
