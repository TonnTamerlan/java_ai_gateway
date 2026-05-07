# Step 3 — AI Gateway sync chat path

**Status:** Pending.
**Prereqs:** Step 2 green (interfaces defined).

## Scope

Wire `OpenAiChatProvider` (Spring AI) inside `ai-gateway-core`. Expose a streaming REST endpoint.

- `provider-openai` module: implement `ChatProvider` against Spring AI's `ChatClient` for OpenAI.
- `ai-gateway-core`: `POST /v1/chat` (or similar) returning `Flux<ChatChunk>` SSE.
- Spring slice tests with `@WebFluxTest`.
- Integration test using **WireMock** to stub OpenAI streaming responses (no live OpenAI in CI).
- Add `OPENAI_API_KEY` reading + sane defaults.

## First questions

1. Endpoint shape: `POST /v1/chat` body? Path? OpenAPI shape via springdoc?
2. Tool-calling support in this step or deferred?
3. Token usage reporting back to caller?
4. Failure mapping: how does a ChatProvider failure surface over SSE?
