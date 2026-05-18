package com.typedgoose.calc;

import com.typedgoose.calc.api.FileInput;
import com.typedgoose.calc.api.SummarizeResponse;
import com.typedgoose.calc.db.FilesRepository;
import com.typedgoose.calc.db.JobsRepository;
import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.RequestPublisher;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.calc.domain.SummarizationJob;
import com.typedgoose.calc.domain.SummarizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "calc.kafka.listener.enabled=false"
})
class SummarizationServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("goose")
            .withUsername("goose")
            .withPassword("goose");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    static final List<UUID> publishedCorrelationIds = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-05-18T19:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RequestPublisher recordingPublisher() {
            return file -> publishedCorrelationIds.add(file.correlationId());
        }
    }

    @Autowired
    SummarizationService service;

    @Autowired
    JobsRepository jobs;

    @Autowired
    FilesRepository files;

    @Test
    void submitPersistsJobPlusFilesAndPublishesEachOne() {
        publishedCorrelationIds.clear();

        SummarizeResponse response = service.submit("summarize each", List.of(
                new FileInput("a.txt", "first file body"),
                new FileInput("b.txt", "second file body"),
                new FileInput("c.txt", "third file body")));

        assertThat(response.jobId()).isNotNull();
        assertThat(response.correlationIds()).hasSize(3).doesNotContainNull();

        SummarizationJob job = jobs.findById(response.jobId()).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.fileCount()).isEqualTo(3);

        List<SummarizationFile> rows = files.findByJobId(job.id());
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.status()).isEqualTo(FileStatus.PENDING);
            assertThat(row.instruction()).isEqualTo("summarize each");
            assertThat(row.summary()).isNull();
            assertThat(row.model()).isNull();
            assertThat(row.promptTokens()).isNull();
            assertThat(row.completionTokens()).isNull();
        });
        assertThat(rows.stream().map(SummarizationFile::correlationId).toList())
                .containsExactlyInAnyOrderElementsOf(response.correlationIds());

        assertThat(publishedCorrelationIds).containsExactlyInAnyOrderElementsOf(response.correlationIds());
    }

    @Test
    void markDoneTransitionsPendingRowAndIsIdempotent() {
        SummarizeResponse response = service.submit("summarize", List.of(
                new FileInput("hello.txt", "hello world")));
        UUID correlationId = response.correlationIds().getFirst();
        Instant now = Instant.parse("2026-05-18T19:05:00Z");

        int firstUpdate = files.markDone(correlationId, "summary text", "gpt-4o-mini", 12, 7, now);
        int replayUpdate = files.markDone(correlationId, "different", "gpt-4o", 999, 999, now);

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(replayUpdate).isEqualTo(0);

        SummarizationFile stored = files.findByCorrelationId(correlationId).orElseThrow();
        assertThat(stored.status()).isEqualTo(FileStatus.DONE);
        assertThat(stored.summary()).isEqualTo("summary text");
        assertThat(stored.model()).isEqualTo("gpt-4o-mini");
        assertThat(stored.promptTokens()).isEqualTo(12);
        assertThat(stored.completionTokens()).isEqualTo(7);
    }

    @Test
    void markFailedTransitionsPendingRowAndIsIdempotent() {
        SummarizeResponse response = service.submit("summarize", List.of(
                new FileInput("a.txt", "lorem ipsum")));
        UUID correlationId = response.correlationIds().getFirst();
        Instant now = Instant.parse("2026-05-18T19:10:00Z");

        int firstUpdate = files.markFailed(correlationId, "upstream rate limited", now);
        int replayUpdate = files.markFailed(correlationId, "different error", now);

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(replayUpdate).isEqualTo(0);

        SummarizationFile stored = files.findByCorrelationId(correlationId).orElseThrow();
        assertThat(stored.status()).isEqualTo(FileStatus.FAILED);
        assertThat(stored.errorMessage()).isEqualTo("upstream rate limited");
    }
}
