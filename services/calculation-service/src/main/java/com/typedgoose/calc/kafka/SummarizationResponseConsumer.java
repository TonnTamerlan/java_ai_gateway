package com.typedgoose.calc.kafka;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.contracts.summarization.SummarizationResponseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class SummarizationResponseConsumer {

    private final FilesRepository files;
    private final JobsRepository jobs;
    private final Clock clock;

    @KafkaListener(
            topics = KafkaConfig.SUMMARIZATION_RESPONSES_TOPIC,
            containerFactory = "responseListenerContainerFactory",
            autoStartup = "${calc.kafka.listener.enabled:true}")
    @Transactional
    public void onMessage(SummarizationResponseMessage message) {
        Instant now = clock.instant();
        int updated = switch (message.status()) {
            case DONE -> files.markDone(
                    message.correlationId(),
                    message.summary(),
                    message.model(),
                    message.promptTokens(),
                    message.completionTokens(),
                    now);
            case FAILED -> files.markFailed(
                    message.correlationId(),
                    message.errorMessage() == null ? "unknown error" : message.errorMessage(),
                    now);
        };

        if (updated == 0) {
            log.info("response consumer: file already terminal correlationId={}",
                    message.correlationId());
            return;
        }
        log.info("response consumer: marked {} correlationId={} jobId={}",
                message.status(), message.correlationId(), message.jobId());
        rollUpJob(message.jobId(), now);
    }

    private void rollUpJob(UUID jobId, Instant now) {
        List<SummarizationFile> rows = files.findByJobIdAndDeletedAtIsNullOrderByCreatedAt(jobId);
        if (rows.isEmpty()) return;

        boolean allTerminal = rows.stream().allMatch(r ->
                r.status() == FileStatus.DONE || r.status() == FileStatus.FAILED);
        if (!allTerminal) return;

        boolean anyFailed = rows.stream().anyMatch(r -> r.status() == FileStatus.FAILED);
        boolean anyDone = rows.stream().anyMatch(r -> r.status() == FileStatus.DONE);
        JobStatus next = anyFailed && anyDone
                ? JobStatus.PARTIAL
                : anyFailed
                    ? JobStatus.FAILED
                    : JobStatus.DONE;
        jobs.updateStatus(jobId, next, now);
        log.info("response consumer: rolled up job status jobId={} status={}", jobId, next);
    }
}
