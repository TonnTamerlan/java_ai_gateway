# Step 5 — Frontend chat UI

**Status:** Pending.
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
