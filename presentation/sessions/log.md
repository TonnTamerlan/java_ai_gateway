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
