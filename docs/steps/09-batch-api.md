# Step 9 — OpenAI Batch API (`OpenAiBatchProvider`)

**Status:** Done — 2026-05-19 (commit `bda3612`).
**Prereqs:** Step 8 green.

## Scope

ai-gateway consumes summarization requests from Kafka, batches them through the
OpenAI Batch API, polls for completion, and publishes results back to Kafka.
calculation-service consumes responses and persists them — replacing the Step 8
echo loop.

- `BatchProvider` interface + envelopes (`BatchSubmission`, `BatchHandle`,
  `BatchStatus`, `BatchResult`, `ProviderException`) in `shared-contracts` —
  vendor-pure per ADR D3.
- `OpenAiBatchProvider` in `provider-openai` using the official
  **`com.openai:openai-java`** SDK (Files + Batches APIs).
- Lifecycle: render JSONL (`custom_id = correlationId`) → upload with
  `FilePurpose.BATCH` → create batch on `/v1/chat/completions` with
  `completion_window=24h` → poll → download output JSONL → fan-out per-line.
- Flyway `V1__ai_schema.sql` creates schema `ai` + `batch_correlations`
  (`correlation_id`, `job_id`, `file_id`, `provider_batch_id`, `instruction`,
  `content`, `status`, `submitted_at`, `completed_at`, `version`).
- Kafka batch listener with `max-poll-records=5`, `fetch-min-bytes=5000`,
  `fetch-max-wait=180000ms` — approximates "5 messages OR ~3 min". Pins every
  batch item to `ModelTier.FAST` (cheapest tier — summaries don't need GPT-4o).
- `BatchPoller` `@Scheduled(fixedDelayString="${app.ai.batch.poll-interval-ms}")`
  short-circuits when no rows are open in `batch_correlations` (no idle OpenAI
  calls). When a batch completes it publishes one
  `SummarizationResponseMessage` per result to `summarization-responses` and
  marks the row DONE/FAILED.
- calc-service `SummarizationResponseConsumer` listens on
  `summarization-responses`, calls `files.markDone` / `files.markFailed`, and
  rolls up the job status.

## Resolved questions

1. **Wait for real Batch or stub?** Real Batch API, no stub — but document the
   24h SLA caveat for the live demo (small inputs typically finish in minutes).
2. **Single batch per N requests, or one per request?** One batch per Kafka poll
   window (5 messages OR ~3 min). Lower OpenAI API churn.
3. **Local-demo timeout?** None enforced in code; the demo runs with
   `completion_window=24h` and the audience sees minute-scale completions for
   small inputs. If a batch genuinely stalls, restart the stack — the
   `batch_correlations` rows persist and the poller resumes.

## Demo-latency caveat

A low-traffic trickle can sit up to ~3 min on the broker before the consumer
flushes (the spec). For a live demo, push ≥5 files in a burst to trigger
immediate flush.
