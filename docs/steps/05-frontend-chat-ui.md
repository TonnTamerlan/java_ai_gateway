# Step 5 — Frontend chat UI

**Status:** Done (2026-05-12, partially — merged with Steps 2–4). Router, `/chat/:id` routes, TanStack Query, MSW stubs, and cancellation UX deferred. ChatPanel now streams SSE via `fetch` + `ReadableStream` (not `EventSource`, since POST + body is required) with a model-tier `Select`; per-conversation history lives in chat-service memory.
**Prereqs:** Step 4 green.

## Scope

Replace the landing page with a real chat panel.

- React Router skeleton with at least `/`, `/chat/:id`.
- Chat panel: message list, input box, send button, streaming token rendering.
- Native `EventSource` consumption with cancellation on unmount.
- Loading / error / reconnect states.
- TanStack Query for chat-list and chat-detail.
- Vitest component tests + MSW stubs.

## First questions

1. Server-rendered chat list page or client-only?
2. Cancellation UX (stop button behaviour during stream)?
3. Optimistic message rendering before server ACK?
