# ADR 0009 — Test strategy

- **Status:** Accepted
- **Date:** 2026-05-08

## Decision

Per Java service:

| Layer | Tooling | TDD? |
| --- | --- | --- |
| Unit | JUnit 5 + AssertJ + Mockito | Yes |
| Spring slice | `@WebFluxTest`, `@DataJpaTest`, `@JsonTest`, embedded Kafka | Yes |
| Integration | Spring Boot test + Testcontainers (Postgres, Kafka, ES) + WireMock for OpenAI | Test-after fine |
| Contract | Pact JVM (consumer + provider, REST + Kafka message) | Consumer-driven |
| Mutation (optional) | PIT on `shared-contracts` and provider mappers | n/a |

- JaCoCo gate **80% line / 75% branch** on unit + slice combined. Gates start in Step 6.
- Integration coverage not counted (different runtime).

Frontend:

| Layer | Tooling |
| --- | --- |
| Unit / component | Vitest + React Testing Library |
| API stub | MSW; Pact JS consumer tests starting Step 12 |
| End-to-end | Playwright (1 worker, headless) — Step 6 + Step 10 |

## Step 1 reality

- Each Spring service ships **one** trivial `contextLoads` test today. JaCoCo runs but no threshold yet.
- Frontend ships one Vitest spec covering the landing render and the API status tag.
- Testcontainers and Pact join in their respective steps.
