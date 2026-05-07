# Step 7 — Calculation Service skeleton + Kafka topics

**Status:** Pending.
**Prereqs:** Step 6 green.

## Scope

First Kafka usage. No AI yet on this path.

- `kafka-init` one-shot container creates `calc.requests`, `calc.results`, `ai.requests`, `ai.responses`, `goose.dlq`.
- Calculation Service consumes `calc.requests`, performs a stub calculation (no AI), publishes `calc.results`.
- Flyway migration creates schema `calc` + `calc_steps`.
- API Service `POST /api/jobs` produces to `calc.requests`; `GET /api/jobs/{id}` reads `calc_jobs` from `app` schema.
- Slice + Testcontainers Kafka tests.

## First questions

1. Topic partitions/replication factor for local single-broker?
2. JSON envelopes (introduced in Step 2) — any changes needed for calc payload?
3. DLQ policy on bad messages?
