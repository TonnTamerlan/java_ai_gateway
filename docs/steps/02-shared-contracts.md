# Step 2 — `shared-contracts` (domain interfaces + Kafka envelopes)

**Status:** Pending.
**Prereqs:** Step 1 green.

## Scope

Define the vendor-agnostic API the rest of the system codes against.

- `ChatProvider` (sync request/response + `Flux<ChatChunk>` streaming).
- `BatchProvider` (`submit`, `poll`, `fetchResults`).
- Domain types: `ChatRequest`, `ChatChunk`, `ChatResponse`, `BatchSubmission`, `BatchHandle`, `BatchStatus`, `BatchResult`, `ProviderException`.
- Kafka envelope types for `ai.requests` / `ai.responses` / `calc.requests` / `calc.results` (correlation ID, headers, payload).
- Unit tests on Jackson serialization of every type, including round-trip.
- Module stays vendor-pure: no Spring AI, no OpenAI SDK, no Anthropic SDK imports.

## First questions to ask in Step 2's brainstorm

1. JSON serialization shape: snake_case or camelCase? (Affects later interop with OpenAI Batch JSONL.)
2. Tool/function-calling representation: model in `ChatRequest` now, or defer to Step 3?
3. Errors: a single `ProviderException` with `kind` enum, or a small hierarchy?
4. Kafka envelope wrapper: separate from payload (recommended) or flat?
5. Streaming: cold `Flux<ChatChunk>` or hot `Sinks.Many` — does it matter at the contract level?
