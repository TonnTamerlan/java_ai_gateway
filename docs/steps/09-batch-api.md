# Step 9 — OpenAI Batch API (`OpenAiBatchProvider`)

**Status:** Pending.
**Prereqs:** Step 8 green.

## Scope

Heavy-calculation path uses OpenAI's 24h Batch API for cost savings.

- `OpenAiBatchProvider` in `provider-openai` using **official `openai-java` SDK** (Files API + Batches API).
- Lifecycle: upload JSONL → create batch → poll → download output → parse JSONL → fan-out per-line results.
- Flyway migration creates schema `ai` + `batch_correlations(correlation_id, batch_id, file_id, request_index, status, result_payload, …)`.
- `BatchPoller` scheduled bean polls open batches (only when there ARE open batches — no idle polling).
- On completion, results published to `ai.responses` with correlation IDs that match Calculation Service's pending requests.
- Integration test using either `wiremock` for the OpenAI Batch endpoints, or a fake controller that emulates the batch lifecycle deterministically.

## First questions

1. Wait for actual OpenAI Batch (slow) in CI, or always stub?
2. Single batch per N requests, or one batch per request?
3. Batch SLA (24h) — what's our local-demo timeout?
