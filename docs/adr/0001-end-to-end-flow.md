# ADR 0001 — End-to-end flow

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

The system must demonstrate two interaction patterns suitable for a meet-up: a synchronous low-latency chat experience and an asynchronous heavy-calculation pipeline. Both ultimately consult AI.

## Decision

- **Chat: synchronous REST + SSE.** Frontend → API Service → AI Gateway → OpenAI. Tokens stream back over SSE.
- **Calculation: asynchronous Kafka.** Frontend → API Service → `calc.requests` → Calculation Service. While processing, Calculation Service uses Kafka request/reply (`ai.requests` / `ai.responses` with correlation IDs) against AI Gateway. Final results published to `calc.results`; API Service updates `calc_jobs` row, browser observes via TanStack Query polling.

## Consequences

- Two distinct patterns visible on stage (sync REST/SSE vs async Kafka request/reply).
- API Service runs WebFlux for the SSE endpoint; the same service uses MVC-style polling endpoints for jobs.
- Correlation-ID handling in `batch_correlations` becomes a deliberate teaching moment.
