package com.typedgoose.calc.domain;

import com.typedgoose.calc.api.FileInput;
import com.typedgoose.calc.api.SummarizeRequest;
import com.typedgoose.calc.api.SummarizeResponse;
import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
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

    @Transactional
    public SummarizeResponse submit(SummarizeRequest request) {
        Instant now = clock.instant();
        UUID jobId = UUID.randomUUID();
        jobs.insert(new SummarizationJob(jobId, now, now, JobStatus.PENDING, request.files().size()));

        List<UUID> correlationIds = new ArrayList<>(request.files().size());
        List<SummarizationFile> persisted = new ArrayList<>(request.files().size());
        for (FileInput input : request.files()) {
            UUID correlationId = UUID.randomUUID();
            SummarizationFile file = new SummarizationFile(
                    UUID.randomUUID(),
                    jobId,
                    correlationId,
                    input.content(),
                    input.instruction(),
                    FileStatus.PENDING,
                    null, null, null, null, null,
                    now, now);
            files.insert(file);
            correlationIds.add(correlationId);
            persisted.add(file);
        }

        // Publish AFTER the transaction commits would be safer once the broker is wired (Step 08).
        // For the no-op Step-07 publisher there is no transactional concern; we publish in-band.
        persisted.forEach(publisher::publish);

        log.info("summarization job submitted jobId={} fileCount={}", jobId, request.files().size());
        return new SummarizeResponse(jobId, correlationIds);
    }
}
