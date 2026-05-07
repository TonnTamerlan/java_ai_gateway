# ADR 0006 — PostgreSQL data model

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

Three services need to persist different concerns. We want schema-per-service ownership without operating multiple databases.

## Decision

- One Postgres container, three logical schemas:
  - `app` — owned by API Service: `chats`, `messages`, `calc_jobs`.
  - `calc` — owned by Calculation Service: `calc_steps`.
  - `ai` — owned by AI Gateway: `batch_correlations`.
- Each service brings its own Flyway migrations under `src/main/resources/db/migration/`.
- **Step-1 reality:** Postgres starts empty. The schemas above are the planned target shape; they are not pre-created. Each service materialises its own schema in the session it first needs persistence (Step 4 onwards).
- Tables are expected to evolve during implementation.

## Consequences

- A migration in service A cannot reach into service B's schema; cross-service joins live in app code.
- One container, one volume, one set of credentials — simple operationally.
- Migrations stay reviewable per-service.
