package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.JobStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import com.typedgoose.calc.domain.SummarizationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "calc.kafka.listener.enabled=false"
})
class FilesRepositorySearchIT {

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

    @Autowired
    JobsRepository jobs;

    @Autowired
    FilesRepository files;

    UUID jobId;

    @BeforeEach
    void seed() {
        // Clean state — Spring Boot does not auto-truncate between tests.
        files.deleteAll();
        jobs.deleteAll();

        Instant base = Instant.parse("2026-05-19T10:00:00Z");
        jobId = UUID.randomUUID();
        jobs.save(new SummarizationJob(jobId, null, base, base, JobStatus.PENDING, 5));

        seedFile("alpha.txt",  FileStatus.PENDING, base.plusSeconds(10), "first prompt",  null);
        seedFile("beta.txt",   FileStatus.DONE,    base.plusSeconds(20), "second prompt", "summary B");
        seedFile("Gamma.TXT",  FileStatus.DONE,    base.plusSeconds(30), "third prompt",  "summary G");
        seedFile("delta.txt",  FileStatus.FAILED,  base.plusSeconds(40), "fourth prompt", null);
        seedFile("alphabet.txt", FileStatus.PENDING, base.plusSeconds(50), "fifth prompt",  null);
    }

    private void seedFile(String name, FileStatus status, Instant at, String prompt, String summary) {
        files.save(new SummarizationFile(
                UUID.randomUUID(), null, jobId, UUID.randomUUID(),
                name, "body of " + name, prompt, status,
                summary, summary == null ? null : "gpt-4o-mini",
                summary == null ? null : 100,
                summary == null ? null : 50,
                status == FileStatus.FAILED ? "boom" : null,
                at, at));
    }

    @Test
    void searchWithNoFiltersReturnsAllPaged() {
        Page<SummarizationFile> page = files.search(null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "fileName")));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(SummarizationFile::fileName)
                .containsExactly("Gamma.TXT", "alpha.txt"); // 'G' < 'a' in ASCII
    }

    @Test
    void searchSortsByCreatedAtDescending() {
        Page<SummarizationFile> page = files.search(null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).extracting(SummarizationFile::fileName)
                .containsExactly("alphabet.txt", "delta.txt", "Gamma.TXT", "beta.txt", "alpha.txt");
    }

    @Test
    void searchFiltersByStatus() {
        Page<SummarizationFile> page = files.search(FileStatus.DONE, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(SummarizationFile::fileName)
                .containsExactly("beta.txt", "Gamma.TXT");
    }

    @Test
    void searchByNameSubstringIsCaseInsensitive() {
        Page<SummarizationFile> page = files.search(null, "ALPHA",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fileName")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(SummarizationFile::fileName)
                .containsExactly("alpha.txt", "alphabet.txt");
    }

    @Test
    void searchCombinesStatusAndNameSubstring() {
        Page<SummarizationFile> page = files.search(FileStatus.PENDING, "alpha",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fileName")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(SummarizationFile::fileName)
                .containsExactly("alpha.txt", "alphabet.txt");
    }
}
