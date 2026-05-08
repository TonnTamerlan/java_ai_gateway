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
