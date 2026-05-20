package com.typedgoose.calc.domain;

import com.typedgoose.calc.api.FileInput;
import com.typedgoose.calc.api.SummarizeResponse;
import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final JobsRepository jobs;
    private final FilesRepository files;
    private final RequestPublisher publisher;
    private final Clock clock;
    private final Tracer tracer;

    @Transactional
    public SummarizeResponse submit(String prompt, List<FileInput> inputs) {
        Instant now = clock.instant();
        UUID jobId = UUID.randomUUID();
        // SummarizationFile.correlationId (per-file UUID, Kafka message key) is a separate
        // concept from the MDC `correlationId` baggage minted at api-gateway. Don't conflate.
        try (var ignored = tracer.createBaggageInScope("jobId", jobId.toString())) {
            jobs.save(new SummarizationJob(jobId, null, now, now, JobStatus.PENDING, inputs.size()));

            List<UUID> correlationIds = new ArrayList<>(inputs.size());
            List<SummarizationFile> persisted = new ArrayList<>(inputs.size());
            for (FileInput input : inputs) {
                UUID correlationId = UUID.randomUUID();
                SummarizationFile file = new SummarizationFile(
                        UUID.randomUUID(),
                        null,
                        jobId,
                        correlationId,
                        input.filename(),
                        input.content(),
                        prompt,
                        FileStatus.PENDING,
                        null, null, null, null, null,
                        now, now, null);
                files.save(file);
                correlationIds.add(correlationId);
                persisted.add(file);
            }

            // Publishing in-band is fine for the echo consumer; once a real broker is wired
            // a TransactionSynchronization callback would defer it past commit.
            persisted.forEach(publisher::publish);

            log.info("summarization job submitted jobId={} fileCount={}", jobId, inputs.size());
            return new SummarizeResponse(jobId, correlationIds);
        }
    }
}
