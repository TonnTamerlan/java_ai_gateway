# Step CHANGELOG

One-line entry per completed step. Append at the bottom; never rewrite.

- 2026-05-08 — Step 1 — Repo skeleton, walking compose, ADRs D1–D9.
- 2026-05-08 — Step 01b — Discovery + edge gateway pivot, chat-service skeleton, ADR D10.
- 2026-05-11 — First end-to-end chat message round-trip: frontend ChatPanel ↔ `POST /api/chats/messages` ↔ chat-service hardcoded reply.
- 2026-05-12 — Steps 2–5 merged: shared-contracts AI types + sealed `ChatChunk`, provider-openai (Spring AI 2.0 OpenAiChatModel), ai-gateway WebFlux SSE endpoint `POST /v1/chat/stream`, chat-service in-memory `ConversationStore` + SSE relay, frontend model picker + streaming SSE parser.
- 2026-05-13 — Added Lombok 1.18.46 via base `goose.java-conventions` plugin; replaced SLF4J logger + explicit-constructor boilerplate in `ai-gateway-core` `ChatController`, `OpenAiChatProvider`, and `chat-service` `ChatController` with `@Slf4j` + `@RequiredArgsConstructor`. `lombok.config` opts Spring `@Value` into copyable annotations so field-level `@Value` propagates to the generated constructor parameter.
- 2026-05-13 — Partial Step 11 (logging slice): wired `logstash-logback-encoder` 8.0 via `goose.spring-boot-conventions` and shipped a per-service `logback-spring.xml` with CONSOLE always-on and FILE + async LOGSTASH appenders gated to the `docker` Spring profile. Fixed the Logstash pipeline (`%{[service]:-unknown}` was invalid; switched to `action => "create"` against the built-in `logs-*-*` data-stream template so each service gets its own data stream). Added a one-shot `kibana-init` compose service that POSTs `infra/kibana/saved-objects.ndjson` (data view `logs-*` + saved search "All service logs") to Kibana. End-to-end verified: all 6 services land in their own `.ds-logs-<service>-default-*` data stream and show up in Kibana Discover. OTel→Jaeger and SBA polish remain deferred to full Step 11.
