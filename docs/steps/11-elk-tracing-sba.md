# Step 11 — ELK + Jaeger + Spring Boot Admin (cross-cutting)

**Status:** Partially done — logging slice landed on 2026-05-13. Remaining: OTel→Jaeger traces, full SBA polish, and the dashboards/searches bundle beyond the minimal one we shipped.
**Prereqs:** Step 10 green.

## Done in the 2026-05-13 partial pass

- `logstash-logback-encoder` 8.0 added to `gradle/libs.versions.toml` and wired through `goose.spring-boot-conventions.gradle` so every Spring service picks it up.
- Identical per-service `src/main/resources/logback-spring.xml` (6 copies): always-on `CONSOLE`; `FILE` (rolling, gzip, `/var/log/app/<service>.log`) and async `LOGSTASH` (TCP → `logstash:5000`) gated to the `docker` Spring profile, because the file appender opens its target eagerly and `/var/log/app` isn't writable on dev machines.
- `SPRING_PROFILES_ACTIVE=docker` set on every application container in `infra/docker/docker-compose.yml`.
- `infra/logstash/pipeline.conf`: replaced the invalid `%{[service]:-unknown}` syntax with a proper `if ![service]` fallback filter, and switched the ES output to `action => "create"` so writes land in the built-in `logs-*-*` data-stream template — each service ends up in its own `logs-<service>-default` data stream, and the Kibana data view `logs-*` covers them all.
- `infra/kibana/saved-objects.ndjson` ships a data view (`logs-*`, time field `@timestamp`) and a Discover saved search ("All service logs", columns `service`, `level`, `logger_name`, `message`). A one-shot `kibana-init` compose service POSTs it to `/api/saved_objects/_import?overwrite=true` after Kibana is healthy.
- Verified end-to-end: `docker compose up`, hit health endpoints, all six services appear in `_cat/indices/.ds-logs-*` and in Kibana Discover.

## Still deferred

- Correlation-id `WebFilter` populating `MDC` (`correlationId`, `chatId`/`jobId`). The encoder already lists those `includeMdcKeyName`s, so they'll start showing up automatically once a filter populates them.
- OpenTelemetry → Jaeger instrumentation across REST + Kafka + DB + Spring AI.
- Spring Boot Admin dashboards and live log-level toggling.
- Richer Kibana saved objects (per-flow dashboards: chat-trace, calc-job-trace).

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
