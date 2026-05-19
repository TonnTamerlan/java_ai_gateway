package com.typedgoose.aigateway.summarization;

import com.typedgoose.aigateway.kafka.KafkaConfig;
import com.typedgoose.contracts.ai.BatchHandle;
import com.typedgoose.contracts.ai.BatchProvider;
import com.typedgoose.contracts.ai.BatchResult;
import com.typedgoose.contracts.ai.BatchStatus;
import com.typedgoose.contracts.summarization.SummarizationResponseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class BatchPoller {

    private final BatchProvider batchProvider;
    private final BatchCorrelationsRepository correlations;
    private final KafkaTemplate<String, SummarizationResponseMessage> responseTemplate;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.ai.batch.poll-interval-ms:30000}",
            initialDelayString = "${app.ai.batch.poll-interval-ms:30000}")
    public void pollOpenBatches() {
        List<String> openBatchIds = correlations.findOpenProviderBatchIds();
        if (openBatchIds.isEmpty()) {
            return;
        }
        log.debug("polling {} open batches", openBatchIds.size());
        for (String batchId : openBatchIds) {
            try {
                handleBatch(batchId);
            } catch (Exception e) {
                log.warn("polling failed for batchId={}: {}", batchId, e.toString());
            }
        }
    }

    private void handleBatch(String providerBatchId) {
        BatchHandle handle = new BatchHandle(providerBatchId, null);
        BatchStatus status = batchProvider.poll(handle);

        switch (status) {
            case BatchStatus.Pending ignored -> log.debug("batch {} pending", providerBatchId);
            case BatchStatus.InProgress ignored -> log.debug("batch {} in-progress", providerBatchId);
            case BatchStatus.Completed ignored -> finalizeCompleted(handle);
            case BatchStatus.Failed failed -> finalizeFailed(handle, failed.reason());
        }
    }

    private void finalizeCompleted(BatchHandle handle) {
        List<BatchResult> results = batchProvider.fetchResults(handle);
        List<BatchCorrelation> rows = correlations.findByProviderBatchId(handle.providerBatchId());
        Map<UUID, BatchCorrelation> byCorrelation = new HashMap<>();
        for (BatchCorrelation row : rows) {
            byCorrelation.put(row.correlationId(), row);
        }

        Instant now = clock.instant();
        for (BatchResult result : results) {
            BatchCorrelation row = byCorrelation.get(result.correlationId());
            if (row == null) {
                log.warn("orphan result: correlationId={} not found in batchId={}",
                        result.correlationId(), handle.providerBatchId());
                continue;
            }
            Optional<String> error = result.error();
            SummarizationResponseMessage msg = new SummarizationResponseMessage(
                    row.jobId(),
                    row.fileId(),
                    row.correlationId(),
                    error.isPresent()
                            ? SummarizationResponseMessage.Status.FAILED
                            : SummarizationResponseMessage.Status.DONE,
                    error.isPresent() ? null : result.summary(),
                    error.isPresent() ? null : result.model(),
                    result.promptTokens(),
                    result.completionTokens(),
                    error.orElse(null));
            responseTemplate.send(
                    KafkaConfig.SUMMARIZATION_RESPONSES_TOPIC,
                    row.correlationId().toString(),
                    msg);

            if (error.isPresent()) {
                correlations.markFailed(row.correlationId(), now);
            } else {
                correlations.markDone(row.correlationId(), now);
            }
        }
        log.info("finalized batch: batchId={} results={}",
                handle.providerBatchId(), results.size());
    }

    private void finalizeFailed(BatchHandle handle, String reason) {
        List<BatchCorrelation> rows = correlations.findByProviderBatchId(handle.providerBatchId());
        Instant now = clock.instant();
        for (BatchCorrelation row : rows) {
            SummarizationResponseMessage msg = new SummarizationResponseMessage(
                    row.jobId(),
                    row.fileId(),
                    row.correlationId(),
                    SummarizationResponseMessage.Status.FAILED,
                    null,
                    null,
                    0,
                    0,
                    "batch " + reason);
            responseTemplate.send(
                    KafkaConfig.SUMMARIZATION_RESPONSES_TOPIC,
                    row.correlationId().toString(),
                    msg);
            correlations.markFailed(row.correlationId(), now);
        }
        log.warn("batch failed: batchId={} reason={} rows={}",
                handle.providerBatchId(), reason, rows.size());
    }
}
