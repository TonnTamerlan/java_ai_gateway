# Step CHANGELOG

One-line entry per completed step. Append at the bottom; never rewrite.

- 2026-05-08 — Step 1 — Repo skeleton, walking compose, ADRs D1–D9.
- 2026-05-08 — Step 01b — Discovery + edge gateway pivot, chat-service skeleton, ADR D10.
- 2026-05-11 — First end-to-end chat message round-trip: frontend ChatPanel ↔ `POST /api/chats/messages` ↔ chat-service hardcoded reply.
- 2026-05-12 — Steps 2–5 merged: shared-contracts AI types + sealed `ChatChunk`, provider-openai (Spring AI 2.0 OpenAiChatModel), ai-gateway WebFlux SSE endpoint `POST /v1/chat/stream`, chat-service in-memory `ConversationStore` + SSE relay, frontend model picker + streaming SSE parser.
