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
