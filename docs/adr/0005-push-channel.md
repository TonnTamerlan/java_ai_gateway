# ADR 0005 — Browser push channel

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

Two distinct events need to reach the browser: streaming chat tokens (high-frequency, latency-sensitive) and calc-job status changes (low-frequency, eventual).

## Decision

- **Chat:** Server-Sent Events. `GET /api/chats/{chatId}/stream` returns `text/event-stream`. API Service consumes `Flux<ChatChunk>` from AI Gateway and re-emits to the browser.
- **Jobs:** HTTP polling. Browser polls `GET /api/jobs/{jobId}` every ~1.5 s via TanStack Query `refetchInterval` while status is `PENDING|RUNNING`; stops once terminal.
- API Service runs **WebFlux** throughout for a uniform reactive runtime.

## Consequences

- Native `EventSource` in browser → no extra SSE library, automatic reconnect.
- nginx proxy needs SSE-safe headers (`proxy_buffering off`, `proxy_read_timeout 1h`, `X-Accel-Buffering: no`) on `/api/.../stream` routes — already wired in Step 1's `nginx.conf`.
- Polling for jobs is more requests on the wire but trivially correct; no per-job long-lived connection.
