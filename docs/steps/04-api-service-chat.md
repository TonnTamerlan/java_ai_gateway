# Step 4 — API Service chat plumbing + persistence

**Status:** Done (2026-05-12, partially — merged with Steps 2, 3, 5). Persistence + Flyway schema deferred to a later step (user explicitly asked for no DB). chat-service holds history in-memory via `ConversationStore` for now.
**Prereqs:** Step 3 green.

## Scope

API Service becomes the chat BFF.

> **Carry-over from Step 1:** api-service currently depends on `spring-boot-starter-web` (MVC). Step 1 switched away from `spring-boot-starter-webflux` because Spring Boot Admin 4.0.0's auto-configured `RegistrationClient` requires a `RestTemplateBuilder` and refuses to start under pure WebFlux. Step 4 must either (a) reintroduce WebFlux and provide an explicit `ReactiveRegistrationClient` bean, or (b) keep MVC and use `ResponseBodyEmitter` / `SseEmitter` for the chat stream. Decide as part of this step's brainstorm.

- Flyway migration creates schema `app` + `chats(id, title, created_at)` + `messages(id, chat_id, role, content, tokens, created_at)`. (First time DB is materialised.)
- WebFlux endpoints: `POST /api/chats`, `POST /api/chats/{id}/messages`, `GET /api/chats/{id}/stream`.
- WebClient wired to AI Gateway; SSE relay browser ↔ gateway.
- Slice + integration tests with Testcontainers Postgres + WireMock for AI Gateway.

## First questions

1. WebFlux throughout, or MVC for non-stream + WebFlux for stream?
2. Persistence: Spring Data R2DBC (reactive) vs JPA (blocking, simpler)?
3. Chat history: send full history or just user message + persist server-side?
