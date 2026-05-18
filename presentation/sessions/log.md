# Meet-up notes — Typed Goose

Curated talk material. Append-only. Most recent at the bottom.

## 2026-05-08 — Eight small fixes between `./gradlew build` ✅ and `docker compose up --wait` ✅
_The gap between "my unit tests pass" and "every container is healthy on a teammate's laptop" is where local-dev stories actually live. This session collected a clean cascade of eight real, separately diagnosable failures — each one a 60-second stage moment._

### Built / fixed
- Step 1 walking skeleton: 11-container compose, 4 Spring Boot 4 services, AntD landing, ELK + Jaeger + Spring Boot Admin all green via a single `docker compose -f infra/docker/docker-compose.yml up --wait`.
- Per-service Dockerfiles + a multi-stage nginx that bakes the Vite bundle: `services/{ai-gateway/core,api-service,calculation-service}/Dockerfile`, `infra/spring-boot-admin/Dockerfile`, `infra/nginx/{Dockerfile,nginx.conf}`.
- Compose + healthchecks across the whole stack: `infra/docker/docker-compose.yml`.
- Deviation log captured in `docs/steps/01-skeleton.md` "Deviations from the original plan" section — same eight items, this time written for future Claude Code sessions.

### Decisions and trade-offs
- **Builder base = `eclipse-temurin:21-jdk`, runtime = `:25-jre`** — Gradle 8.14.3's bundled Groovy can't re-parse Java 25 bytecode (`major version 69`). Toolchain still provisions Java 25 via Foojay during the build; service jars run on Java 25. Plan said Java 25 throughout; reality wanted a Java 21 daemon.
- **Per-service Gradle cache id** in each Dockerfile (`--mount=type=cache,id=gradle-<service>,target=/root/.gradle`). One shared cache mount made parallel docker builds collide on `/root/.gradle/caches/journal-1/journal-1.lock`. Cost: ~500 MB of duplicate dependency cache. Worth it.
- **api-service swapped to `spring-boot-starter-web`** for Step 1 — Spring Boot Admin 4.0.0's auto-configured `RegistrationClient` won't start under pure WebFlux (no `RestTemplateBuilder`). Step 4 must reintroduce WebFlux + provide an explicit `ReactiveRegistrationClient`. Carry-over recorded in `docs/steps/04-api-service-chat.md`.
- **Kafka image: `apache/kafka:3.8.0`** (not Bitnami). Bitnami's free Kafka tags are gone from Docker Hub; `bitnami/kafka:3.7`, `:3.8`, `:3.8.0` all 404 now. Apache's official image needs `KAFKA_*` env vars + a fixed `CLUSTER_ID` instead of Bitnami's `KAFKA_CFG_*`.

### Surprises / aha moments
- `eclipse-temurin:25-jre` ships **neither `curl` nor `wget`** — every Spring healthcheck failed silently with no log noise; container stayed `health: starting` until retries exhausted. _Stage cue: `docker exec ... which curl` returns nothing; show `apt-get install curl` fix in the Dockerfile._
- macOS reserves host port **5000** for AirPlay Receiver. Logstash binding `5000:5000` got `bind: address already in use`; remapped to `5044:5000` (in-network services still hit `logstash:5000`). _Stage cue: `lsof -i :5000` on Mac before the demo._
- nginx alpine, served on IPv4 only (`listen 80;`), gets "Connection refused" from its own `wget http://localhost/` healthcheck because alpine resolves `localhost` to `::1` first. Fix is one extra line: `listen [::]:80;`. _Stage cue: tail the failing healthcheck log, then add IPv6, watch it flip to healthy in 10 s._
- The "exit code 0" lie — `docker compose up --wait --build` running in background reported `exit 0` even when Kafka/Jaeger image pulls 404'd, even when api-service exited (1) on startup. Always look at `compose ps` and `docker logs`, not the bash exit code.

### Code worth showing
- `services/ai-gateway/core/Dockerfile:1-19` — clean multi-stage with cache-mounted Gradle build; explains the `21-jdk` builder + `25-jre` runtime split in 19 lines.
- `infra/docker/docker-compose.yml:30-58` — Apache Kafka 3.8.0 KRaft config with hardcoded `CLUSTER_ID`. Worth pausing on — beginners always ask about `CLUSTER_ID`.
- `services/api-service/build.gradle:14-23` — the SBA-vs-WebFlux comment lives in code where the next session can't miss it.
- `infra/nginx/nginx.conf:36-49` and `:54-63` — SSE-safe block (`proxy_buffering off`, `X-Accel-Buffering no`) sitting next to the regular `/api/` proxy. Already wired before any SSE code exists; Step 4 doesn't have to revisit nginx.

### Demo flow this session enables
1. `./gradlew build` → green. (Toolchain provisions Java 25; ~40 s.)
2. `docker compose -f infra/docker/docker-compose.yml up --wait` → all 11 containers go healthy in ~6 minutes the first time, ~90 s warm.
3. Open `http://localhost/` — AntD landing card with API status tag flipping to green.
4. Open `http://localhost:9000/applications` (Spring Boot Admin) — three services registered.
5. Tear it down with `down -v`, walk through the 8-deviation list with the `docs/steps/01-skeleton.md` "Deviations" section on screen.

---

## 2026-05-08 — Discovery dissolves the SBA / WebFlux blocker

_Step 1 ate a deliberate compromise: `api-service` had to be MVC, not WebFlux, because Spring Boot Admin 4.0's auto-configured `RegistrationClient` needs `RestTemplateBuilder`. The pivot to Eureka — which I picked for completely unrelated reasons — flips SBA from "pushed to" to "discovery client". The blocker just evaporates. That's the talk-worthy beat: a routing redesign quietly fixed a runtime conflict the whole project was carrying._

### Built / fixed
- **api-service → api-gateway** — pure Spring Cloud Gateway (WebFlux, Eureka client). Two declarative routes, one `RouterFunction` for `/api/health`, ~50 LOC of YAML + 12 LOC of Java. `services/api-gateway/`.
- **New `services/chat-service/`** — empty WebFlux skeleton. Will own chat REST + SSE relay; planted now so Step 4 has a place to land.
- **New `infra/eureka-server/`** — five-line standalone Eureka, port 8761.
- **ai-gateway is stateless** — explicit decision: drops the `ai` Postgres schema from D6, never reachable from the FE, talks to chat-service via REST and to calculation-service via Kafka.
- **SBA → discovery client** — `@EnableDiscoveryClient` + an Eureka block; drop `spring-boot-admin-starter-client` from every service. `infra/spring-boot-admin/src/main/java/com/typedgoose/sba/SpringBootAdminApplication.java`.
- **ADR D10** records the pivot, supersedes part of D5, amends D1/D6/D8: `docs/adr/0010-discovery-and-edge-gateway.md`.

### Decisions and trade-offs
- **Pure SCG, no BFF in the gateway.** Tempting to keep one WebFlux process for both routing and chat — but conflating routing with persistence/SSE relay is exactly the smell the pivot was about. chat-service splits out cleanly; calculation-service grows its own jobs REST. Cost: one more service. Benefit: edge stays free of business logic.
- **Static `/api/health` via `RouterFunction`, not a forward to actuator.** SCG's `forward:/actuator/health` works but is one more thing to debug if it doesn't. A three-line bean returning `{"status":"UP"}` is unambiguous and keeps the FE's existing `fetch('/api/health')` working through the rename.
- **Two schemas (`chat`, `calc`), not three.** `app` and `ai` both die — `ai-gateway` owns no data; chat-service owns `chat` directly (no D6-style "app" pseudo-namespace).
- **SCG declarative routes with `discovery.locator.enabled: false`.** Auto-routing every Eureka service through SCG sounds nice, leaks every internal service to the FE. Whitelist what's exposed.

### Surprises / aha moments
- **The Step 1 deviation note in `docs/steps/01-skeleton.md` lines 52-53 is now obsolete — and not because we did the work it asked for.** Step 4 was supposed to "reintroduce WebFlux + provide an explicit `ReactiveRegistrationClient`". Instead, SBA-via-discovery sidesteps the problem entirely. _Stage cue: open the deviation note, then `docs/adr/0010-discovery-and-edge-gateway.md` line 49 — the carry-over kills itself._
- **Spring Cloud 2025.0.0 has stale Boot 3 package refs and silently breaks under Boot 4.** Build compiles fine; `@SpringBootTest` context refresh fails with `ClassNotFoundException: org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration`. Boot 4 moved that class to `org.springframework.boot.webmvc.autoconfigure`. SC 2025.0 was the last train for Boot 3.x. Bump to **2025.1.1**, plus rename `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`. _Stage cue: show the test stack trace, then the two-line `gradle/libs.versions.toml` diff that fixes both._
- **`proxy_pass http://api_gateway/;` vs `proxy_pass http://api_gateway;` — one trailing slash flips the routing semantics.** Pre-pivot nginx stripped `/api/` because api-service was the only backend. Post-pivot SCG needs the full path so its `Path=/api/chats/**` predicate matches. Drop the slash; preserve the prefix; SCG handles `StripPrefix=1` itself. _Stage cue: diff `infra/nginx/nginx.conf:34-67`._
- **`docker compose up --wait` exit-0 with stale code.** First run after deleting `spring-boot-admin-starter-client`: ai-gateway and calc-service logs were still throwing `RestClientRegistrationClient` errors from the SBA push client. Cached image. `docker compose build` (without `--no-cache`) was enough — the build context invalidated correctly once forced. Compose's `--build` flag is non-default and easy to forget when you've been running `up --wait` for hours.

### Code worth showing
- `services/api-gateway/src/main/resources/application.yml:5-23` — declarative SCG routes with `lb://chat-service` and `lb://calculation-service`. Reads like a reverse proxy config, runs on Reactor Netty.
- `services/api-gateway/src/main/java/com/typedgoose/gateway/HealthRouteConfig.java` — three-line `RouterFunction`. Smallest possible "compatibility shim".
- `infra/spring-boot-admin/src/main/java/com/typedgoose/sba/SpringBootAdminApplication.java` — `@EnableDiscoveryClient` is the entire migration from push to pull.
- `gradle/libs.versions.toml` — the `springCloud = "2025.1.1"` line and the `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux` artifact rename. Both real-world Boot-4-upgrade gotchas.
- `docs/diagrams/architecture.mmd` — the post-pivot topology, `Eureka` node with dotted `register` arrows from every Java service.

### Demo flow this session enables
1. Show the deviation note in `docs/steps/01-skeleton.md:52-53` — the WebFlux/SBA conflict the project was carrying.
2. `docker compose -f infra/docker/docker-compose.yml up --wait` — 13 containers healthy.
3. `curl http://localhost:8761/eureka/apps | jq '.applications.application[].name'` — five Java services registered.
4. `curl http://localhost/api/health` → `{"status":"UP"}` (FE health probe still works through the rename).
5. `curl -i http://localhost/api/chats/anything` → 404 from chat-service. SCG resolved `lb://chat-service` via Eureka, forwarded; chat-service has no controller yet so 404 is the *correct* answer. (This is the routing proof.)
6. Open SBA at `http://localhost:9000/` — five services, all discovered via Eureka, no client starter on any of them. Compare with the Step 1 demo where SBA showed three services pushed in by their clients.

---

## 2026-05-11 — Three silent migrations only a browser test could surface

_Built a 50-LOC chat dialog. All tests green, both builds green, two CI-style gates green — and every browser request still 404'd. The bug wasn't in the new code. It was in three independent Spring Boot 4 / Cloud 5 / dev-tooling migrations that the project had been carrying since the pivot two days earlier, all silently. The talk beat: **a passing unit test proves what your unit does; only a real client proves your wiring._

### Built / fixed
- **End-to-end chat round-trip** — `POST /api/chats/messages` → chat-service hardcoded `"Message got"` reply, rendered as a chat bubble. Frontend `ChatPanel` (~135 LOC) in `apps/frontend/src/components/ChatPanel.tsx`. Backend `ChatController` + records in `services/chat-service/src/main/java/com/typedgoose/chat/api/`. Slice test + 5 React tests.
- **Enter sends, Shift+Enter and Cmd+Enter newline** — single keydown handler, deliberately no platform branch (`metaKey` is Cmd-only on Mac, `shiftKey` works everywhere). `ChatPanel.tsx:74-80`.
- **api-gateway routes config migrated to SCG 5.0 prefix** — `services/api-gateway/src/main/resources/application.yml` moved from `spring.cloud.gateway.routes` to `spring.cloud.gateway.server.webflux.routes` (and the discovery-locator sibling).
- **Vite dev proxy made dev-compose-aware** — `apps/frontend/vite.config.ts` now reads `VITE_API_PROXY_TARGET`; `infra/docker/docker-compose.dev.yml` sets it to `http://api-gateway:8080`. Native `pnpm dev` still works against the host-published 8080.
- **chat-service gets a `spring-boot-webflux-test` testImplementation** — Boot 4 split test slices into per-module artifacts; `spring-boot-starter-test` no longer brings `@WebFluxTest` in. `services/chat-service/build.gradle`.

### Decisions and trade-offs
- **`@WebFluxTest` slice, not `@SpringBootTest`+`@AutoConfigureWebTestClient`** for the controller test. The full-context option avoids the extra dependency but loads Eureka client + actuator + everything. Per-controller slice is ~10× faster and matches CLAUDE.md's "TDD is rigid for unit + slice layers". Cost: one explicit `testImplementation` line in `chat-service/build.gradle`.
- **Inline `Layout.Footer` for the chat, not a `position: fixed` overlay.** User chose the option that pushes content up over the floating-bottom overlay. Trade-off: chat scrolls off if page content gets long. Acceptable for a demo with a single landing card.
- **`disabled={sending}` on the textarea while waiting** — noted in the advisor pass as a UX smell once real AI calls land (8 s latency = "broken keyboard" feeling). Kept for the stub; explicit follow-up for Step 3.

### Surprises / aha moments
- **Spring Cloud Gateway 5.0.x silently renamed `spring.cloud.gateway.routes` to `spring.cloud.gateway.server.webflux.routes`.** No startup warning, no validation failure, `/actuator/env` still shows the old keys loaded — they just bind to nothing. Symptom: every request returns Spring's default 404 JSON. Took unzipping `spring-cloud-gateway-server-webflux-5.0.1.jar` and grep'ing `META-INF/spring-configuration-metadata.json` to find the canonical key. _Stage cue: `git show HEAD~1 -- services/api-gateway/src/main/resources/application.yml` — six-line YAML reshuffle that revives 100% of traffic. Then `curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("application.yml")) | .properties | keys'` before-and-after._
- **Spring Boot 4 removed `WebFluxTest` from `spring-boot-test-autoconfigure`.** In Boot 3 the jar shipped `jdbc/`, `json/`, `web/reactive/`, `web/servlet/`, etc. In Boot 4 the jar ships **only `jdbc/` and `json/`** — every web slice was extracted into a separately published `spring-boot-{webflux,webmvc}-test` artifact, with a new package: `org.springframework.boot.webflux.test.autoconfigure.WebFluxTest`. `spring-boot-starter-test` does not pull these transitively. _Stage cue: `unzip -l ~/.gradle/caches/.../spring-boot-test-autoconfigure-4.0.0.jar | awk '{print $4}' | grep -oE 'autoconfigure/[a-z]+/' | sort -u` — two lines, both lowercase, neither says "web"._
- **Vite dev proxy and "localhost" mean different things in different containers.** `vite.config.ts` had `target: 'http://localhost:8080'` — fine when `pnpm dev` runs on the host, but inside the dev-compose container `localhost` is the container's own loopback where nothing is listening. Symptom: browser → Vite OK, Vite → `localhost:8080` ECONNREFUSED, HTTP 500. Env-var fallback (`VITE_API_PROXY_TARGET`) lets the same `vite.config.ts` resolve `api-gateway:8080` via Docker DNS in compose, and stays at `localhost:8080` for native runs. _Stage cue: `docker exec goose-frontend-1 sh -c 'curl -sS -X POST http://localhost:8080/api/chats/messages -d "{}"'` (fails), then the same against `http://api-gateway:8080` (works)._
- **Tests + build + type-check were all green. The advisor told me to run a browser anyway.** That's the moment that mattered. Three separate things were broken in production-like wiring; the unit and slice tests passed against each component's isolated contract. CLAUDE.md already said "if you can't test the UI, say so explicitly rather than claiming success" — I almost shipped the implicit "done" claim on green tests alone.

### Code worth showing
- `services/api-gateway/src/main/resources/application.yml:6-23` — the new `spring.cloud.gateway.server.webflux.routes` prefix. Diff against the prior version is a single nesting level deeper; finding it cost an hour.
- `services/chat-service/build.gradle:17` — `testImplementation 'org.springframework.boot:spring-boot-webflux-test'`. One line, but it's the only place this Boot-4 migration trap lives.
- `services/chat-service/src/test/java/com/typedgoose/chat/api/ChatControllerTest.java:5` — `import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;`. Put it next to a Boot 3 example in the slide to make the package shift obvious.
- `apps/frontend/src/components/ChatPanel.tsx:74-80` — the four-line key handler that does all three modifier cases without a `navigator.platform` branch.
- `apps/frontend/vite.config.ts:4-6` — `VITE_API_PROXY_TARGET ?? 'http://localhost:8080'`. Smallest possible "dev URL depends on where dev runs" fix.
- The `memory/scg5_property_prefix.md` and `memory/boot4_test_slice_packages.md` files I dropped into claude-mem so the *next* session catches this in seconds instead of an hour.

### Demo flow this session enables
1. Open the chat panel in the browser at `http://localhost:5173` — type "ping", press Enter, "Message got" comes back. (~3 s round-trip end-to-end through Vite proxy → SCG → Eureka load-balanced lb://chat-service.)
2. Try Shift+Enter — multi-line input, no submission.
3. `docker compose stop chat-service`, repeat — `"Failed to reach chat-service"` bubble in ~10 s (load-balancer timeout).
4. Now the migration story: `git log --oneline -3`, then `git show HEAD~1 -- services/api-gateway/src/main/resources/application.yml` — show the silent rename.
5. Open `~/.gradle/caches/modules-2/files-2.1/org.springframework.boot/spring-boot-test-autoconfigure/4.0.0/.../*.jar` in a viewer — show that only `jdbc/` and `json/` subpackages exist. The web slices live elsewhere now.
6. Close with the advisor-call beat: tests green, build green, almost shipped. The browser test was the only thing that caught any of it.

---

## 2026-05-12 — First real LLM call: stub → live OpenAI streaming through three Spring services
_The "wire it to OpenAI" session. Four planned step files (2–5) collapsed into one slice because the scope was actually one feature. What's talk-worthy isn't that it works — it's the **stack tax** the bleeding-edge stack levied along the way: Spring AI 2.0 only as `-SNAPSHOT`, Jackson 3's split-personality package, Mockito vs. Java 25, MockWebServer vs. `Unsafe`. Each "obvious upgrade" triggered a small refactor. The demo proof is a `curl` that finishes with `event: error` and a real OpenAI 401 — proving three reactive hops are wired before a single token has streamed._

### Built / fixed
- **`shared-contracts` AI types** — `ModelTier`, `MessageRole`, `ChatMessage`, `ChatStreamRequest`, sealed `ChatChunk { Delta, Done, Error }`, `ChatProvider`. Vendor-pure (no Spring AI imports). Jackson 3 polymorphic round-trip tests. `services/shared-contracts/src/main/java/com/typedgoose/contracts/ai/`.
- **`provider-openai`** — Spring AI 2.0-SNAPSHOT `ChatClient`/`ChatModel`. `OpenAiChatProvider` maps `ChatStreamRequest` → `Prompt` with `OpenAiChatOptions.builder().model(...).streamUsage(true).build()`, adapts `Flux<ChatResponse>` → `Flux<ChatChunk>`, filters empty role-only chunks, emits `Done(usage)` only when tokens > 0, maps upstream errors to `Error("provider_error", ...)`. `services/ai-gateway/provider-openai/src/main/java/com/typedgoose/aigateway/openai/OpenAiChatProvider.java`.
- **`ai-gateway/core` switched MVC → WebFlux** — new `POST /v1/chat/stream` returning `Flux<ServerSentEvent<ChatChunk>>`, `ModelTierProperties` (`@ConfigurationProperties("app.ai.models")`) maps FAST/MEDIUM/SLOW → `gpt-4o-mini`/`gpt-4o`/`gpt-4-turbo`. INFO-level structured logs at request received + response complete (latency, tokens). `services/ai-gateway/core/src/main/java/com/typedgoose/aigateway/`.
- **`chat-service` rewrote stub → SSE relay** — `ConversationStore` (immutable-list-in-`ConcurrentHashMap` via `compute`, per-conversation `Semaphore`), `WebClientConfig` (`@LoadBalanced WebClient.Builder` + `aiGatewayClient` to `lb://ai-gateway`), `ChatController` that appends user turn, calls upstream, accumulates assistant content, appends assistant turn on `Done`, **does not append on `Error` or cancel**. System prompt hardcoded via `app.chat.system-prompt`. `services/chat-service/src/main/java/com/typedgoose/chat/`.
- **`api-gateway` SSE-safe tuning** — `spring.cloud.gateway.server.webflux.httpclient: { response-timeout: 120s, compression: false }`. Gzip on SSE breaks per-token flushing.
- **Frontend** — Antd `Select` for FAST/MEDIUM/SLOW, `crypto.randomUUID` (with non-HTTPS fallback) per-tab `conversationId`, `fetch` + `ReadableStream` SSE parser handling split chunks + CRLF, deltas appended to last assistant bubble, errored bubbles styled red. `apps/frontend/src/components/ChatPanel.tsx`.

### Decisions and trade-offs
- **State lives in chat-service; ai-gateway stays stateless** (matches ADR 0010). Cost: each turn forwards the full history. Benefit: `calculation-service` can call ai-gateway over Kafka in Step 7 without inheriting chat-conversation state.
- **System prompt is server-side, not on the wire.** Frontend can pick model tier but cannot inject system messages. `MessageRole` on the wire is `USER` / `ASSISTANT` only — drops `SYSTEM` entirely to make this structurally impossible.
- **Per-conversation `Semaphore` → 409 on concurrent turns**, not append-and-let-the-LLM-deal-with-it. The "two user turns, no assistant in between" interleave was a real race the advisor pass surfaced before any code was written.
- **Sealed-interface `ChatChunk` with `@JsonTypeInfo(use=NAME, property="type")`** — gives tool calls (`ToolCallDelta` variant) and explicit `Error` an obvious shape today. Flat `{delta?, done?, usage?}` would have grown an optional field every release.
- **Spring AI 2.0.0-SNAPSHOT pinned in `gradle/libs.versions.toml`** — 1.1 GA only supports Boot 3.x; 2.0 GA isn't published yet. Added `https://repo.spring.io/snapshot` to `settings.gradle` and `goose.java-conventions.gradle`. Acceptable for a study project; absolutely not for production.

### Surprises / aha moments
- **Spring AI 2.0 supports Boot 4, but it's a moving target.** Maven Central tops out at `1.1.0` GA. `repo.spring.io/snapshot` had `2.0.0-SNAPSHOT-294` rebuilt at **10:06 UTC today**, ~30 minutes before I needed it. Pin or weep. _Stage cue: `curl repo.spring.io/snapshot/org/springframework/ai/spring-ai-openai/2.0.0-SNAPSHOT/ | grep '.jar"' | tail -3` — three timestamped builds from this morning._
- **Jackson 3.0.2 split its packages but kept annotations on Jackson 2.** `ObjectMapper` moved to `tools.jackson.databind.ObjectMapper`. `@JsonTypeInfo` and `@JsonSubTypes` are **still** in `com.fasterxml.jackson.annotation` — and `jackson-databind 3.0.2` transitively pulls `jackson-annotations 2.20`, not the half-built `3.0-rc5`. So `shared-contracts/build.gradle` declares two Jackson coordinates from two different groups. _Stage cue: `curl -sL https://repo1.maven.org/maven2/tools/jackson/jackson-bom/3.0.2/jackson-bom-3.0.2.pom | grep -A1 'jackson.version.annotations'` returns `<jackson.version.annotations>2.20</jackson.version.annotations>` with comment "but keep using 2.x one, latest 2.x released"._
- **Spring Boot 4 deleted `@MockBean`.** The slice tests use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`). Drop into a copy-pasted Boot 3 example and the import silently doesn't exist; the symptom is "this slice test won't compile" with no migration hint. _Stage cue: `git blame` the `import org.springframework.test.context.bean.override.mockito.MockitoBean;` line in `services/ai-gateway/core/src/test/java/com/typedgoose/aigateway/api/ChatControllerTest.java`._
- **Mockito can't mock `OpenAiChatModel` under Java 25 — and that's a good thing.** Byte Buddy 1.15 only supports class file 68 (Java 24); Java 25 is class file 69. Combined with `OpenAiChatModel` being `final`, the inline mock maker dies on init with `IllegalArgumentException: Java 25 (69) is not supported`. The forced refactor — accept `ChatModel` (interface) in `OpenAiChatProvider` instead — is **architecturally cleaner**: providers should depend on the abstraction, not the concrete implementation. The compiler told me what the code review should have. _Stage cue: the diff at `services/ai-gateway/provider-openai/src/main/java/com/typedgoose/aigateway/openai/OpenAiChatProvider.java` — one type signature change, an entire forbidden coupling removed._
- **MockWebServer 5.3.2 imploded on `okhttp3.internal._UtilJvmKt` via `sun.misc.Unsafe`.** Same Java 25 family of issues. Pivoted to a Spring-native stub: a `WebClient.builder().exchangeFunction(req -> ...)` that pops a queued `UpstreamStub` per test. Zero native deps, faster, and the test reads cleaner because the SSE body is a String literal next to the assertions. Subtle bonus: the static-init failure was hidden behind `NoClassDefFoundError: Could not initialize class com.typedgoose.chat.api.ChatControllerTest` — six tests failed with no class-load message. Took digging into the HTML report. _Stage cue: show the `dependencies` block diff in `services/chat-service/build.gradle` — `testImplementation libs.mockwebserver` deleted, no other test imports needed._
- **Spring AI 2.0's `OpenAiChatOptions.StreamOptions` is a record** — accessors are `includeUsage()`, not `getIncludeUsage()`. Mockito-style "set it and assume it" doesn't help; you have to read the bytecode. _Stage cue: `javap -public OpenAiChatOptions\$StreamOptions` — five lines of record accessors._
- **The negative-path smoke is the structural proof.** With `OPENAI_API_KEY=replace-me-in-step-3`, the `curl` returns:
  ```
  event:error
  data:{"type":"error","code":"provider_error","message":"...UnauthorizedException: 401: Incorrect API key..."}
  ```
  That single response proves: api-gateway routed `/api/chats/**` to chat-service; chat-service's `@LoadBalanced WebClient` resolved `lb://ai-gateway` through Eureka; ai-gateway loaded Spring AI's OpenAI auto-config; the upstream HTTP error mapped through `onErrorResume` to a `ChatChunk.Error`; SSE wrote `event: error` correctly; chat-service relayed without appending a partial assistant turn. **Three reactive hops, all verified, before a real key is even issued.**

### Code worth showing
- `services/shared-contracts/src/main/java/com/typedgoose/contracts/ai/ChatChunk.java` — 21 lines, sealed interface + `@JsonSubTypes`. Jackson 3 reads this and routes Delta/Done/Error by the `"type"` discriminator with zero glue code.
- `services/ai-gateway/provider-openai/src/main/java/com/typedgoose/aigateway/openai/OpenAiChatProvider.java:48-80` — the `toChunks` adapter. Two filters that matter: empty `getText()` (the OpenAI role-only first chunk), and `prompt > 0 || completion > 0` (Spring AI fills a non-null `Usage` of zeros into the default `ChatResponseMetadata`, so checking `!= null` alone emits a phantom Done after every delta).
- `services/chat-service/src/main/java/com/typedgoose/chat/api/ChatController.java:54-94` — the SSE relay. `doOnNext` accumulates assistant content into a `StringBuilder`; `doOnComplete` is the **only** place that appends the assistant turn, and only if `!errored`. `onErrorResume` maps WebClient errors to a synthetic `Error` chunk so even an upstream HTTP 500 produces a clean SSE `event: error`. `doOnCancel` releases the semaphore on client disconnect.
- `services/chat-service/src/main/java/com/typedgoose/chat/conversation/ConversationStore.java` — 40 lines. `compute((k, prev) -> List.copyOf(append(prev, msg)))` for atomic mutation; per-conversation `Semaphore(1)` for serialization. No threading library beyond `java.util.concurrent`.
- `apps/frontend/src/components/ChatPanel.tsx:166-200` (`consumeSse`) — `fetch + ReadableStream` SSE parser. ~30 lines. Handles split chunks, CRLF, multi-line `data:`, and the trailing-block case. Vitest has a dedicated test for split-across-`read()` (the most common real-world bug).
- `services/api-gateway/src/main/resources/application.yml:7-12` — `compression: false` and `response-timeout: 120s` under the SCG WebFlux httpclient. Three lines that prevent two of the three "SSE gets buffered" stories.

### Demo flow this session enables
1. Open the browser at `http://localhost` — empty chat dialog now has a model dropdown above the input.
2. Pick `FAST`, type "Say hi in 3 words", hit Enter. Without an OpenAI key: a red error bubble with the upstream message in a couple of seconds. With a real key: tokens stream into the assistant bubble word by word.
3. From a host shell: `curl -N -X POST http://localhost:8080/api/chats/messages -H 'Accept: text/event-stream' -H 'Content-Type: application/json' -d '{"conversationId":"smoke-1","model":"FAST","message":"hi"}'`. With dummy key, the response ends in `event: error` with the OpenAI 401 message — proves the wire is complete even though OpenAI rejected the call. With a real key, watch `event: delta` / `data: {"type":"delta","text":"..."}` lines arrive in real time.
4. Tail `docker compose logs -f chat-service ai-gateway` while sending another turn — both services log structured turn boundaries (`turn_started conversation=83bb2f53 turn=1 model=FAST` … `turn_completed conversation=83bb2f53 latencyMs=1766 errored=false`). The hashed conversation id is identical at both hops because chat-service hashes it locally and ai-gateway logs the same input.
5. Send a follow-up message in the same browser tab — the model gets the prior turn in its context (because chat-service forwarded the full `messages[]` array). Refresh the tab → new `conversationId`, model has forgotten.
6. Open a second tab, fire one message, immediately open dev-tools and fire a second `fetch` against `/api/chats/messages` with the **same** `conversationId` — second request gets HTTP 409. Per-conversation `Semaphore` working.
7. Close with the stack-tax beat: pull up `gradle/libs.versions.toml`, point at `springAi = "2.0.0-SNAPSHOT"`, `jackson-annotations 2.20` next to `jackson-databind 3.0.2`, then show the one-line `chatModel: OpenAiChatModel → ChatModel` diff that's both a Mockito workaround **and** the cleaner architecture.

---

## 2026-05-13 — A 5-line Lombok refactor surfaces two silent failures
_The change was trivial: drop `@Slf4j` + `@RequiredArgsConstructor` onto three classes. The interesting part is the two things that broke quietly along the way — both of which any Spring + Gradle team will hit eventually. **Lombok's default copyable-annotations list does not include Spring's `@Value`**, and **Gradle's build cache happily serves stale `.class` files when only `lombok.config` changed**. Together: the "fix" appeared to not fix anything for one whole rebuild cycle._

### Built / fixed
- **Lombok 1.18.46 wired in via the base convention plugin** — one `dependencies { compileOnly + annotationProcessor }` block in `build-logic/src/main/groovy/goose.java-conventions.gradle`, propagates to all 9 modules. Version pinned in `gradle/libs.versions.toml`; consumed in Groovy as `libs.versions.lombok.get()` to match the existing testing-conventions style.
- **`lombok.config` at repo root** — `config.stopBubbling = true` and `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value`. Two lines, but the second one is the entire point.
- **3 classes refactored** — `services/ai-gateway/core/.../api/ChatController.java`, `services/ai-gateway/provider-openai/.../OpenAiChatProvider.java`, `services/chat-service/.../api/ChatController.java`. SLF4J logger fields gone, explicit constructors gone; `chat-service` moves `@Value("${app.chat.system-prompt}")` from a constructor parameter to the `final` field.
- **`docs/steps/CHANGELOG.md`** entry; committed as `e9e5571` on `main`.

### Decisions and trade-offs
- **Apply via base convention plugin, not per-module.** Three classes today, but more services are coming (calculation-service still empty). Adding `compileOnly` once at the root means every future Boot service gets Lombok for free.
- **Main sources only — no `testCompileOnly`/`testAnnotationProcessor`.** Existing test fixtures don't have boilerplate worth replacing, and Lombok-in-tests tends to obscure intent. Trivial to add later if it changes.
- **Records left alone.** DTOs are already records (`ChatChunk`, `ChatMessage`, `ChatStreamRequest`, `Usage`, `MessageRequest`, `ModelTierProperties`). Replacing a record with `@Data` would be a regression — records get accessors/equals/hashCode/toString from the language, no annotation processor required.
- **Pinned 1.18.46.** Lombok added JDK25 support in 1.18.40 (Sept 2025). Older versions compile, but newer language features in source code might silently bypass the processor.

### Surprises / aha moments
- **Spring's `@Value` is NOT in Lombok's default copyable-annotations list.** `@Qualifier` is. `@NonNull` is. JSpecify, Checker, FindBugs nullability annotations all are. But `org.springframework.beans.factory.annotation.Value` — the one annotation every Spring developer will reach for with `@RequiredArgsConstructor` — is not. Symptom: `NoSuchBeanDefinitionException: No qualifying bean of type 'java.lang.String' available… Dependency annotations: {}`. Spring sees an unannotated `String` constructor parameter and tries to autowire by type. _Stage cue: `javap -v ChatController.class | grep -A2 "RuntimeVisibleParameterAnnotations"` before vs. after the `lombok.config` line — the parameter goes from zero annotations to one._
- **Gradle's build cache serves stale `.class` files when only `lombok.config` changes.** The cache key for `compileJava` hashes source files + classpath + compiler args. The `lombok.config` file is read by the annotation processor at compile time but is invisible to Gradle's cache key. Symptom: change `lombok.config`, rerun `./gradlew test` — 7 tests fail with the same error. `./gradlew clean test` — same 7 fail (clean only nukes the local `build/` dir; the cache still has the old `.class`). Fix: `./gradlew clean test --no-build-cache --rerun-tasks`. _Stage cue: a three-step terminal demo. Edit lombok.config, `./gradlew test` → fail, `clean test` → still fail, `clean test --no-build-cache --rerun-tasks` → pass. Same source code each time._
- **The 7-failures-same-stack pattern reads like one bug; it's actually two.** First green→red was Spring `@Value` not copying. After adding `lombok.config`, it was Gradle caching. From the test output they look identical (`NoSuchBeanDefinitionException`, all 7 chat-service tests, identical stack). Easy to assume the lombok.config change didn't work and try a different annotation. The diagnostic that broke the loop: `javap -v` on the cached `.class` file showed the `@Generated` annotation present (Lombok ran) but no parameter annotations (config not picked up by the run that produced the cached artifact).

### Code worth showing
- `lombok.config` (2 lines) — `config.stopBubbling = true` + `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value`. The whole fix.
- `build-logic/src/main/groovy/goose.java-conventions.gradle:18-21` — five lines that opt every current and future module into Lombok. The `libs.versions.lombok.get()` Groovy interpolation is the version-catalog access pattern that already existed for JUnit/AssertJ/Mockito.
- `services/chat-service/src/main/java/com/typedgoose/chat/api/ChatController.java:24-35` — `@RequiredArgsConstructor` + `@Slf4j` on the class, `@Value` on the field. Diff against the prior 17-line explicit constructor is the cleanest visual proof of what Lombok buys.
- The `e9e5571` commit diff overall — `7 files changed, 24 insertions(+), 29 deletions(-)`. Negative line count is the slide.

### Demo flow this session enables
1. `git show e9e5571 --stat` — 7 files, net −5 lines. Open with the "Lombok netted us five fewer lines, what was hard?" hook.
2. `git show e9e5571 -- services/chat-service/src/main/java/com/typedgoose/chat/api/ChatController.java` — show the constructor disappearing and `@Value` moving from parameter to field.
3. Pull up the failing test report from the first run (or rerun locally without `lombok.config` to reproduce). Read the message: `Dependency annotations: {}`. Ask the audience to guess.
4. Reveal `lombok.config` and the copyable-annotations line. Open Lombok's docs page on `lombok.copyableAnnotations`. Point out: `@Qualifier` is in the default list, `@Value` is not. No technical reason — it's just a curated default.
5. The Gradle-cache beat: edit `lombok.config`, run `./gradlew test`, watch it fail. Run `./gradlew clean test`, watch it still fail. Run `./gradlew clean test --no-build-cache --rerun-tasks`, watch it pass. Same source, three runs, only the cache flags changed.
6. Close on the diagnostic: `javap -v` on the cached `.class` file. The `@Generated` annotation proves Lombok ran. The empty parameter-annotation table proves `lombok.config` wasn't applied. That's the moment the two bugs separate.

---

## 2026-05-18 — JdbcTemplate → Spring Data JDBC: records as aggregates, `@Version` to dodge the existsById SELECT
_The hand-written repo layer was small, but the migration surfaces two non-obvious facts: Spring Data JDBC materializes immutable records natively, and `@Version` doubles as a "this is new" signal that skips a SELECT round-trip per save._

### Built / fixed
- Repos became three-line interfaces: `services/calculation-service/src/main/java/com/typedgoose/calc/db/FilesRepository.java`, `…/db/JobsRepository.java`. Net diff: 11 files, **+61 / −145 lines** (commit `d31e383`).
- Records gained `@Id` / `@Version` / `@Table(schema="calc")`: `…/domain/SummarizationFile.java`, `…/domain/SummarizationJob.java`.
- Conditional updates stayed custom — `@Modifying @Query` with `WHERE status = 'PENDING'` to preserve idempotent replay.
- New Flyway migration: `services/calculation-service/src/main/resources/db/migration/V2__add_version_column.sql` — `ADD COLUMN version BIGINT NOT NULL DEFAULT 0` on both tables. Verified live: applied to existing volume without a wipe (`flyway_schema_history` shows the V2 row alongside V1).

### Decisions and trade-offs
- **Records, not entities.** Spring Data JDBC reads the canonical constructor — no setters, no JPA-style proxies. The version component is just another final field. Avoided JPA / Hibernate entirely; the project's "vendor-pure aggregates" rule held.
- **Kept `markDone` / `markFailed` / `updateStatus` as `@Modifying @Query`** rather than read-modify-`save()`. Derived queries can't express `WHERE … AND status = 'PENDING'`. The alternative (load, mutate, save) would also need optimistic-locking retry logic — three lines of SQL is cheaper and the existing idempotency tests passed unchanged.
- **`@Version` over `Persistable<UUID>`.** Both solve "is this entity new?" for app-assigned UUIDs. `Persistable` requires a `@Transient` flag, which fights the record contract. `@Version Long` is a real DB column, costs one BIGINT per row, and the rule "null → INSERT" is a property of the record itself rather than something the caller has to remember.

### Surprises / aha moments
- **Spring Data JDBC's default new-vs-existing detection is `existsById` — i.e. a SELECT before every save.** With an externally generated UUID PK, the `@Id` field is never null, so the default heuristic ("PK is null → new") doesn't apply. Spring falls back to a pre-`save()` `SELECT 1 FROM calc.summarization_jobs WHERE id = ?` to decide whether to emit INSERT or UPDATE. **Adding `@Version` flips the signal:** null version → new (INSERT), non-null → existing (UPDATE), and the existsById SELECT is skipped entirely. Per-row cost: one less round-trip. _Stage cue: enable `logging.level.org.springframework.jdbc=DEBUG`, run a `submit` request twice — once with the `@Version` field, once with it removed — and diff the JDBC trace. The "before" run has 4 statements per file (`SELECT existsById`, then `INSERT`); the "after" has 1 (`INSERT`)._
- **`@Modifying @Query` survives the migration.** Audience expects "Spring Data means no more SQL." The reality is more nuanced — `CrudRepository.save()` handles 80% of writes, and the other 20% (conditional UPDATEs, bulk deletes, set-based ops) drop into `@Modifying @Query` while still living in the same interface. The repo file is still 30 lines, just no `RowMapper` anymore. _Stage cue: show the `FilesRepository.java` diff side-by-side: deleted ROW_MAPPER, deleted `INSERT INTO … VALUES (?, ?, ?, …)`, but the `UPDATE … WHERE status = 'PENDING'` SQL is **still there**, now annotated with `@Modifying @Query`._
- **Records can host `@Id` / `@Version` / `@Table` directly on the record header.** Annotation targets on `RECORD_COMPONENT` plus the existing Spring Data Relational annotations means the entity is literally one record: `record SummarizationJob(@Id UUID id, @Version Long version, Instant createdAt, …)`. No mapper class, no DTO/entity split. The "where does my domain object live?" question evaporates.

### Code worth showing
- `services/calculation-service/src/main/java/com/typedgoose/calc/db/FilesRepository.java` (whole file, 38 lines) — the entire repository surface after migration. Three derived queries, two `@Modifying @Query`, zero `RowMapper`. Diff against the 104-line JdbcTemplate version is the slide.
- `services/calculation-service/src/main/java/com/typedgoose/calc/domain/SummarizationJob.java` (whole file, 16 lines) — one record annotated with `@Table(schema="calc", name="summarization_jobs")`, `@Id`, `@Version`. The "this is also the persistence model" reveal.
- `services/calculation-service/src/main/resources/db/migration/V2__add_version_column.sql` (2 lines) — `ADD COLUMN version BIGINT NOT NULL DEFAULT 0` on each table. The cheapest enabling change in the diff.
- `services/calculation-service/src/main/java/com/typedgoose/calc/domain/SummarizationService.java:32-37` — `jobs.save(new SummarizationJob(jobId, null, now, now, JobStatus.PENDING, inputs.size()))`. The literal `null` for the version slot is the "INSERT please" signal — calling it out makes the `@Version` trick concrete.

### Demo flow this session enables
1. `git show d31e383 --stat` — open with "−145 / +61, and the new migration is two lines."
2. Side-by-side: old `FilesRepository.java` (the 104-line JdbcTemplate version) vs. new (the 38-line interface). Point at where each method went — `findByJobId` → derived query, `markDone` → `@Modifying @Query`, `insert` → inherited `save()`.
3. Pose the question: "If I call `files.save(file)`, how does Spring Data JDBC know whether to INSERT or UPDATE?" Wait. Reveal: `existsById` SELECT round-trip. Audience groans.
4. The `@Version` reveal: null version → new, non-null → existing. Pull up the JDBC debug log to show the SELECT is gone.
5. Switch to the V2 migration file — two ALTER statements. Then `docker compose exec postgres psql -c "SELECT version, description, success FROM calc.flyway_schema_history;"` — shows V1 + V2 applied, on the same existing volume from prior demos. Sells the "Flyway-as-incremental-migration" story.
6. Close on the idempotency callout: `SummarizationServiceIT.markDoneTransitionsPendingRowAndIsIdempotent` passed unchanged after the migration. The `@Modifying @Query` SQL is byte-identical to the JdbcTemplate version. Spring Data doesn't replace SQL — it absorbs the 80% case so the remaining 20% is more visible.

---
