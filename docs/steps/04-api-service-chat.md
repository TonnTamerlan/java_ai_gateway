# Step 4 — API Service chat plumbing + persistence

**Status:** Pending.
**Prereqs:** Step 3 green.

## Scope

API Service becomes the chat BFF.

- Flyway migration creates schema `app` + `chats(id, title, created_at)` + `messages(id, chat_id, role, content, tokens, created_at)`. (First time DB is materialised.)
- WebFlux endpoints: `POST /api/chats`, `POST /api/chats/{id}/messages`, `GET /api/chats/{id}/stream`.
- WebClient wired to AI Gateway; SSE relay browser ↔ gateway.
- Slice + integration tests with Testcontainers Postgres + WireMock for AI Gateway.

## First questions

1. WebFlux throughout, or MVC for non-stream + WebFlux for stream?
2. Persistence: Spring Data R2DBC (reactive) vs JPA (blocking, simpler)?
3. Chat history: send full history or just user message + persist server-side?
