# Step 8 — Kafka request/reply between Calculation Service ↔ AI Gateway

**Status:** Done — 2026-05-18 (commit `184db71`). Shipped as a calc-service-local Kafka publish + echo loop rather than a `ReplyingKafkaTemplate` request/reply round-trip; the real AI-side replacement landed in Step 9 (OpenAI Batch lifecycle).
**Prereqs:** Step 7 green.

## Scope

Calculation now consults AI via Kafka, not REST.

- Calculation Service uses Spring Kafka `ReplyingKafkaTemplate` to publish on `ai.requests` and await on `ai.responses` keyed by correlation ID.
- AI Gateway consumes `ai.requests`, calls `ChatProvider.complete(...)` (sync, non-streaming for now), publishes to `ai.responses`.
- W3C `traceparent` propagated via Kafka headers (OTel auto-instrumentation).
- Integration test exercises the full request/reply with Testcontainers Kafka.

## First questions

1. Reply timeout default?
2. Use a single ai.responses topic for all callers, or per-caller reply topic?
3. Should the AI Gateway also support sync REST for the same use, or is Kafka the only path now?
