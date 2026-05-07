# ADR 0007 — ELK + observability

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

Need a meet-up-credible logs + tracing setup, locally only.

## Decision

- **Logging:** each Spring service uses `logstash-logback-encoder` with two appenders:
  - `LogstashTcpSocketAppender` → Logstash TCP 5000, wrapped in `AsyncAppender` with reconnect.
  - `RollingFileAppender` → `/var/log/app/<service>.log`, size+time rolling, gzip on roll.
- **Logstash** single pipeline: TCP-JSON in → ES out (`logs-<service>-YYYY.MM.dd`).
- **Elasticsearch** single-node, security disabled.
- **Kibana** with a saved-objects bundle (Step 11).
- **MDC discipline:** every log carries `service`, `correlationId`, `chatId`/`jobId` where applicable, OTel `traceId`/`spanId`.
- **OpenTelemetry tracing** (Spring Boot OTel starter) → Jaeger all-in-one (OTLP). Spans propagate across REST hops, Kafka request/reply (W3C `traceparent`), DB, and Spring AI calls.
- **Spring Boot Admin** as the operator dashboard.
- **No metrics stack** for now; `/actuator/prometheus` enabled for later.

Step 1 ships the infrastructure containers and the SBA registration. Real log/trace flow lights up in Step 11.

## Consequences

- TCP appender's reconnect handles a Logstash restart without crashing services.
- File appender is a backup channel — `tail -f` inside a container always works.
- Jaeger UI on `:16686` carries the trace story; Kibana on `:5601` carries the log story; both correlate via `traceId`.
