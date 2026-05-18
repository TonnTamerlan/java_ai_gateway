package com.typedgoose.calc.kafka;

import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.contracts.summarization.SummarizationRequestMessage;
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

/**
 * Step-08 placeholder: closes the loop locally so the UI sees a job complete.
 * A real consumer in ai-gateway will replace this once the OpenAI Batch path is wired.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class SummarizationEchoConsumer {

    private static final String ECHO_MODEL = "echo-stub";
    private static final int SUMMARY_PREVIEW_CHARS = 80;

    private final FilesRepository files;
    private final JobsRepository jobs;
    private final Clock clock;

    @KafkaListener(
            topics = KafkaConfig.SUMMARIZATION_REQUESTS_TOPIC,
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${calc.kafka.listener.enabled:true}")
    @Transactional
    public void onMessage(SummarizationRequestMessage message) {
        Instant now = clock.instant();
        String preview = message.content() == null
                ? ""
                : message.content().strip();
        if (preview.length() > SUMMARY_PREVIEW_CHARS) {
            preview = preview.substring(0, SUMMARY_PREVIEW_CHARS) + "…";
        }
        String summary = "Echo (" + message.instruction() + "): " + preview;

        int updated = files.markDone(
                message.correlationId(),
                summary,
                ECHO_MODEL,
                null,
                null,
                now);
        if (updated == 0) {
            log.info("echo consumer: file already terminal correlationId={}", message.correlationId());
            return;
        }
        log.info("echo consumer: marked DONE correlationId={} jobId={}",
                message.correlationId(), message.jobId());
        rollUpJob(message.jobId(), now);
    }

    private void rollUpJob(UUID jobId, Instant now) {
        List<SummarizationFile> rows = files.findByJobIdOrderByCreatedAt(jobId);
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
        log.info("echo consumer: rolled up job status jobId={} status={}", jobId, next);
    }
}
