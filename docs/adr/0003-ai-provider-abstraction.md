# ADR 0003 — AI provider abstraction

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

The project must not depend on a specific AI vendor. A future swap to Anthropic / Gemini must not require touching the application code.

## Decision

- Two domain interfaces in `services/shared-contracts`: `ChatProvider` (sync request/response, `Flux<ChatChunk>` streaming) and `BatchProvider` (`submit / poll / fetchResults`).
- Domain types only (`ChatRequest`, `ChatChunk`, `BatchSubmission`, `BatchHandle`, `BatchStatus`, `BatchResult`, `ProviderException`). **`shared-contracts` must not import any vendor SDK type.**
- First implementation in `services/ai-gateway/provider-openai`:
  - **Spring AI 1.x** powers `OpenAiChatProvider` (chat-side ergonomics, streaming, tools).
  - **Official `openai-java` SDK** powers `OpenAiBatchProvider` (Files + Batches lifecycle, which Spring AI doesn't cover).
- Bean wiring uses `@ConditionalOnProperty("app.ai.provider")` so `provider-anthropic` / `provider-gemini` can drop in by config flip.

Step 1 ships the interfaces empty; the implementations land in Steps 2–9.

## Consequences

- Discipline burden: every change to `shared-contracts` must be vendor-agnostic. Reviewers should reject leaks of vendor types.
- Two AI dependencies inside `provider-openai` (Spring AI + openai-java SDK) — acceptable because each is used where it shines.
- Adding Anthropic/Gemini = a new provider sub-module; no changes anywhere else.
