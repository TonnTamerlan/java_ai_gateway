# Step 7 — Calculation Service skeleton (REST + Postgres + Flyway)

**Status:** Done — shipped 2026-05-18.
**Prereqs:** Step 6 green.

## Scope (delivered)

calculation-service goes from a bare `@SpringBootApplication` to a working REST + persistence service. No Kafka yet — that lands in Step 08; the Kafka producer is stubbed behind a `RequestPublisher` seam so Step 08 only swaps an interface implementation.

User flow this step enables:
1. Caller posts N files (full text + per-file instruction) to `POST /api/summarize` through the api-gateway.
2. Service inserts one `calc.summarization_jobs` row (status `PENDING`, `file_count = N`) plus N `calc.summarization_files` rows (one correlation_id each), in a single transaction.
3. Service invokes `RequestPublisher.publish(file)` per row. In Step 07 the default `defaultRequestPublisher` bean is a no-op logger; Step 08 will replace it with a Kafka-backed publisher.
4. `GET /api/summarize/{jobId}` returns the job + files for inspection.

## Files added / changed

| Area | Path |
| --- | --- |
| version catalog | `gradle/libs.versions.toml` — adds `flyway`, `postgres`, `testcontainers`, `awaitility` versions + library aliases |
| build | `services/calculation-service/build.gradle` — adds `spring-boot-starter-validation`, `spring-boot-starter-jdbc`, `spring-boot-flyway` (Boot 4 module), `flyway-core`, runtime `postgresql`, test `spring-boot-webmvc-test`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `awaitility` |
| migration | `services/calculation-service/src/main/resources/db/migration/V1__calc_schema.sql` — `calc` schema; `summarization_jobs`, `summarization_files` |
| API | `com.typedgoose.calc.api.{SummarizeController, SummarizeRequest, SummarizeResponse, FileInput}` — `POST /summarize`, `GET /summarize/{jobId}` with jakarta-validation |
| Domain | `com.typedgoose.calc.domain.{SummarizationService, RequestPublisher, JobStatus, FileStatus, SummarizationJob, SummarizationFile}` |
| Repos | `com.typedgoose.calc.db.{JobsRepository, FilesRepository}` (plain `JdbcTemplate`) |
| Config | `com.typedgoose.calc.config.{CalcConfig, NoOpRequestPublisher}` — `Clock` bean + no-op `RequestPublisher` placeholder |
| application.yml | datasource + flyway sections (env-var driven) |
| api-gateway | route `summarize` → `lb://calculation-service` with `StripPrefix=1` |
| docker-compose | calc-service `depends_on: postgres`, `DB_URL`/`DB_USER`/`DB_PASSWORD` env vars |
| tests | `SummarizeControllerTest` (`@WebMvcTest` slice), `SummarizationServiceIT` (Testcontainers Postgres full round-trip), `CalculationServiceApplicationTests` upgraded to Testcontainers Postgres |

## DB schema (`calc`)

```
summarization_jobs(id UUID PK, created_at, updated_at, status, file_count)
summarization_files(id UUID PK, job_id FK, correlation_id UUID UNIQUE,
                    original_text, instruction, status,
                    summary, model, prompt_tokens, completion_tokens,
                    error_message, created_at, updated_at)
```

Status enums: `JobStatus = {PENDING, PARTIAL, DONE, FAILED}`, `FileStatus = {PENDING, DONE, FAILED}`.

`FilesRepository.markDone(...)` and `markFailed(...)` only update rows still in `PENDING` — idempotent across retries (covered by IT).

## Boot 4 gotchas hit this session

- `@WebMvcTest` annotation lives in `org.springframework.boot.webmvc.test.autoconfigure` and needs the `spring-boot-webmvc-test` artifact explicitly (per memory `boot4_test_slice_packages`).
- `@MockBean` is deprecated in Boot 4; use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`.
- **`FlywayAutoConfiguration` was removed from `spring-boot-autoconfigure` in Boot 4.** Depending on `flyway-core` alone leaves Flyway silently inert — the app starts, no migration logs, queries fail with `relation … does not exist`. Add `org.springframework.boot:spring-boot-flyway` explicitly (BOM-managed). Memory written: `boot4_flyway_artifact`.
- `@ConditionalOnMissingBean` inside a user `@Configuration` is fragile (processing order). Tests that swap a default bean should mark their replacement `@Primary` rather than rely on the condition.

## Verification

- `./gradlew build` — green.
- `./gradlew :services:calculation-service:test` — 7 tests pass (3 controller slice + 3 service IT + 1 context-loads).
- `docker compose -f infra/docker/docker-compose.yml up -d --wait` brings up calc-service healthy alongside postgres, eureka, api-gateway.
- `curl -X POST localhost:8080/api/summarize -d '{"files":[{"instruction":"summarize","content":"hello"}, ...]}'` returns a `jobId` + N `correlationIds`; rows visible in `calc.summarization_files` with `status='PENDING'`; the no-op publisher logs one line per file.
- `curl localhost:8080/api/summarize/{jobId}` returns job + files JSON.

## Deferred to Step 08

- Kafka topics `ai.batch.requests` / `ai.batch.responses` + `goose.dlq`, kafka-init one-shot.
- `RequestProducer` (replaces `defaultRequestPublisher`), `ResponseConsumer` (calls `markDone`/`markFailed`).
- Producer error path: on `KafkaTemplate.send` failure, flip the just-inserted row to FAILED.
- Aggregate job status recompute from file statuses.
- **Dual-write fix:** `SummarizationService.submit` currently invokes the publisher inline inside `@Transactional`. With the Step-07 no-op publisher this is harmless, but once the Kafka producer is plugged in a successful broker send followed by a transaction rollback would create an orphan request on the broker with no DB row. Move the publish to `@TransactionalEventListener(phase=AFTER_COMMIT)` (publish an `AfterFilesPersisted` application event from `submit`) — or document the dual-write risk and accept it.
- **Verification check:** after Step 08 wires its publisher as a `@Component`, a `POST /api/summarize` MUST stop logging `"no-op publisher: would have published…"`. If that line still appears, the `@ConditionalOnMissingBean` on `defaultRequestPublisher` failed to suppress the no-op and the bean ordering needs fixing (drop the conditional and remove the no-op outright when Step 08 lands, or add `@Primary` to the Kafka publisher).
