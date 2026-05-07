# Step 12 — Pact contract tests

**Status:** Pending.
**Prereqs:** Step 11 green.

## Scope

Lock the inter-service contracts.

- API Service ↔ AI Gateway REST: consumer-driven Pact, JUnit 5.
- Calculation Service ↔ AI Gateway Kafka request/reply: Pact message tests on the envelopes.
- Frontend ↔ API Service: Pact JS consumer tests.
- Local Pact broker via `docker-compose.dev.yml` only; canonical pacts live in repo `pact/`.
- Build gate: provider-side verification fails the build on a contract mismatch.

## First questions

1. Pact broker image / version?
2. Where do generated pacts live (committed JSON in `pact/`?)?
3. Verification strategy: every PR vs nightly?
