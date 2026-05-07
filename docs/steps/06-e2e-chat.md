# Step 6 — End-to-end chat slice

**Status:** Pending.
**Prereqs:** Step 5 green.

## Scope

Lock the chat slice with cross-service tests + first JaCoCo gates.

- Spring integration test in api-service stitching together (Testcontainers Postgres) + (real ai-gateway via Boot test contexts) + (WireMock'd OpenAI).
- Playwright e2e: visits `/`, opens a chat, sends a message, asserts streamed reply renders.
- JaCoCo thresholds turn on (80% line / 75% branch on unit + slice combined).

## First questions

1. Playwright runs against `docker compose up` or against a `@SpringBootTest` + Vite preview combo?
2. CI assumption: do we want a `make verify` aggregating gradle + pnpm + e2e?
