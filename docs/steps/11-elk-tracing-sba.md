# Step 11 — ELK + Jaeger + Spring Boot Admin (cross-cutting)

**Status:** Done — logging slice landed 2026-05-13; OTel/MDC/SBA/Kibana slice landed 2026-05-20.
**Prereqs:** Step 10 green.

## Done in the 2026-05-13 partial pass

- `logstash-logback-encoder` 8.0 added to `gradle/libs.versions.toml` and wired through `goose.spring-boot-conventions.gradle` so every Spring service picks it up.
- Identical per-service `src/main/resources/logback-spring.xml` (6 copies): always-on `CONSOLE`; `FILE` (rolling, gzip, `/var/log/app/<service>.log`) and async `LOGSTASH` (TCP → `logstash:5000`) gated to the `docker` Spring profile, because the file appender opens its target eagerly and `/var/log/app` isn't writable on dev machines.
- `SPRING_PROFILES_ACTIVE=docker` set on every application container in `infra/docker/docker-compose.yml`.
- `infra/logstash/pipeline.conf`: replaced the invalid `%{[service]:-unknown}` syntax with a proper `if ![service]` fallback filter, and switched the ES output to `action => "create"` so writes land in the built-in `logs-*-*` data-stream template — each service ends up in its own `logs-<service>-default` data stream, and the Kibana data view `logs-*` covers them all.
- `infra/kibana/saved-objects.ndjson` ships a data view (`logs-*`, time field `@timestamp`) and a Discover saved search ("All service logs", columns `service`, `level`, `logger_name`, `message`). A one-shot `kibana-init` compose service POSTs it to `/api/saved_objects/_import?overwrite=true` after Kibana is healthy.
- Verified end-to-end: `docker compose up`, hit health endpoints, all six services appear in `_cat/indices/.ds-logs-*` and in Kibana Discover.

## Done in the 2026-05-20 OTel/MDC slice

- **Tracing stack**: `spring-boot-starter-opentelemetry` added to `goose.spring-boot-conventions.gradle`; pulls in `spring-boot-micrometer-tracing-opentelemetry`, `spring-boot-opentelemetry`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `micrometer-registry-otlp`. Plus `io.micrometer:context-propagation` for the Reactor↔ThreadLocal bridge.
- **Per-service `application.yml` (6 services)**: 100% sampling, `management.opentelemetry.tracing.export.otlp.endpoint=${JAEGER_OTLP_ENDPOINT:http://jaeger:4317}` (Boot 4's namespace, not Boot 3's `management.otlp.tracing`), `management.tracing.baggage.correlation.fields: correlationId,chatId,jobId` + `remote-fields: correlationId,chatId,jobId`, `management.endpoint.loggers.access: unrestricted` (Boot 4 default tightened to `read_only`; SBA can't `PATCH /actuator/loggers/{name}` without this), `spring.reactor.context-propagation: auto`, and the Sleuth-style `logging.pattern.correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{correlationId:-}] "`.
- **Kafka W3C trace propagation**: `KafkaTemplate.setObservationEnabled(true)` + `containerProperties.setObservationEnabled(true)` on both producer templates and both listener container factories (calc-service `KafkaConfig`, ai-gateway `KafkaConfig`). Producer-side spans (`summarization-requests send`) appear in Jaeger linked to the originating HTTP request. Note: Spring Kafka 3.x batch listeners don't emit a consumer span — records are still processed correctly, just no `receive` span on the consumer side.
- **Correlation-id propagation**: new `services/api-gateway/.../observability/CorrelationIdFilter.java` (`GlobalFilter` at highest precedence) mints `X-Correlation-Id` if missing, propagates downstream as a per-field `correlationId` header (Boot 4's `remote-fields` accepts the simple header on inbound), echoes on the response. Don't use `try-with-resources` around `chain.filter` — Mono subscription happens after the scope closes; header propagation is the reliable reactive path.
- **`jobId` baggage**: `SummarizationService.submit` opens a `tracer.createBaggageInScope("jobId", jobId)` around the persist+publish loop. `BatchPoller.finalizeCompleted`/`finalizeFailed` open the same baggage per row so the Kafka response message carries `jobId` (servlet/scheduler context — try-with-resources is fine here).
- **`chatId` baggage**: `ChatController.postMessage` opens `tracer.createBaggageInScope("chatId", conversationId)` synchronously while assembling the SSE pipeline; `spring.reactor.context-propagation: auto` captures the active observation into Reactor Context at subscription time.
- **SBA Eureka discovery**: kept the existing `@EnableDiscoveryClient` server pattern — services do *not* pull `spring-boot-admin-starter-client` (SBA 4.0 has a known `RegistrationClient` bean-wiring issue with Boot 4 reactive apps). SBA server polls Eureka for the 5 client services and surfaces their actuator endpoints. `/loggers` is unrestricted, so live log-level toggling from the SBA UI works.
- **Kibana saved objects**: three new searches in `infra/kibana/saved-objects.ndjson` — "Chat trace by traceId" (columns include `traceId`/`chatId`/`correlationId`, sort asc to read top-to-bottom), "Calc job trace" (`jobId : *`, sort asc), "Errors only" (`level : ("ERROR" or "WARN")`). The `kibana-init` one-shot compose service re-imports them on stack up.
- **Verified end-to-end on the live stack**: all six services appear in Jaeger; `[<service>,<traceId>,<spanId>,<correlationId>]` populates per-request log lines; submitting a job through api-gateway with `-H "X-Correlation-Id: foo"` produces ES hits filterable by `correlationId: foo` AND `jobId: <uuid>` across calc-service + ai-gateway; SBA `POST /actuator/loggers/com.typedgoose.chat` with `{"configuredLevel":"DEBUG"}` returns 204 and persists.

## Still deferred (out of scope for this round)

- A hand-built **Goose Overview dashboard** in `infra/kibana/saved-objects.ndjson` — Lens NDJSON is brittle to hand-author. Pattern (when needed): build in live Kibana at `localhost:5601`, Stack Management → Saved Objects → Export, replace the file, re-run `kibana-init`.
- **Consumer-side Kafka span** on the Spring Kafka batch listener — appears to be a framework limitation; producer-side spans + jobId baggage in MDC already give us per-job log stitching in Kibana.
- **Prometheus + Grafana** — explicitly out of scope; SBA + Jaeger + Kibana is enough for the meet-up.

## Operator runbook (find a request end-to-end)

1. From Kibana Discover, filter by the `correlationId` or `jobId` field; the saved searches "Calc job trace" and "Chat trace by traceId" pre-populate the right columns.
2. Note the `traceId` from any hit, paste it into Jaeger at `http://localhost:16686` → "Search by Trace ID" to see the cross-service span tree (HTTP + Kafka send + JDBC).
3. To follow up at the service level, open SBA at `http://localhost:9000` → service → Loggers tab → toggle `com.typedgoose.<package>` to `DEBUG`, replay the request, then back to `INFO`.

## Scope

Light up observability across the whole system.

- `logstash-logback-encoder` configured on every service: dual-appender (Logstash TCP + rolling file), MDC pattern (`service`, `correlationId`, `chatId`/`jobId`, OTel `traceId`/`spanId`).
- OTel tracing propagates across REST + Kafka headers + DB + Spring AI.
- Kibana saved-objects bundle (index pattern + Goose Overview dashboard + chat-trace + calc-job-trace searches), imported via init container.
- Jaeger UI demonstrates a full chat-stream trace and a calc-job trace including Kafka request/reply.
- SBA dashboard shows live log-level toggling.

## First questions

1. Importer mechanism for Kibana saved objects?
2. Sample rate for OTel: 100% locally, configurable later?
3. Add Prometheus + Grafana as a stretch?
