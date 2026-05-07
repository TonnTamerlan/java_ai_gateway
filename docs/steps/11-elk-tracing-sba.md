# Step 11 — ELK + Jaeger + Spring Boot Admin (cross-cutting)

**Status:** Pending.
**Prereqs:** Step 10 green.

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
