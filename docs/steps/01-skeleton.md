# Step 1 — Repo skeleton + walking compose

**Status:** Done — 2026-05-08.

## What was created

- Gradle 8.14.3 wrapper, Groovy DSL multi-project (`settings.gradle`, root `build.gradle`, `gradle/libs.versions.toml`).
- `build-logic/` convention plugins: `goose.java-conventions`, `goose.spring-boot-conventions`, `goose.testing-conventions`.
- 4 Spring Boot apps (Boot 4.0.0 / Java 25 toolchain): `ai-gateway-core`, `api-service`, `calculation-service`, `spring-boot-admin`. Each: `@SpringBootApplication`, `application.yml`, one `contextLoads` test, Dockerfile.
- 2 empty Java libs: `shared-contracts`, `ai-gateway-provider-openai`.
- Frontend `apps/frontend`: Vite 6 + React 19 + TypeScript + Ant Design 5 + Vitest 3. One landing page + API status panel + one component test.
- Infra:
  - `infra/docker/docker-compose.yml` (12 containers, all with healthchecks, `service_healthy` deps where it matters).
  - `infra/docker/docker-compose.dev.yml` (Vite dev server override).
  - `infra/nginx/{Dockerfile,nginx.conf}` — multi-stage frontend builder + nginx with SPA fallback, `/api` proxy, SSE-safe headers already in place.
  - `infra/logstash/pipeline.conf` (TCP 5000 in → ES out).
  - `infra/postgres/`, `infra/kafka/`, `infra/elasticsearch/`, `infra/kibana/`, `infra/jaeger/` — `.gitkeep` only; intentionally empty in Step 1.
- Docs: 9 ADRs (D1–D9), step stubs `02`…`13`, diagrams (architecture, chat-sequence), `CHANGELOG.md`.
- `CLAUDE.md` operational guide at repo root.

## Verify

```bash
./gradlew build          # all subprojects compile + contextLoads tests pass
pnpm -C apps/frontend install
pnpm -C apps/frontend test
pnpm -C apps/frontend build

cp infra/docker/.env.example infra/docker/.env
docker compose -f infra/docker/docker-compose.yml up --wait

curl -fsS http://localhost/                              # AntD landing
curl -fsS http://localhost/api/actuator/health           # via nginx → api-service
curl -fsS http://localhost:9000/applications             # SBA registered apps
open http://localhost:5601                               # Kibana (no indices yet)
open http://localhost:16686                              # Jaeger (no traces yet)
```

## Known limitations (intentional)

- No business logic anywhere.
- No domain interfaces in `shared-contracts` — those land in Step 2.
- No OpenAI calls (real or mocked) — Step 3.
- No DB schemas, no Flyway migrations — first one lands in Step 4.
- No Kafka topics created or consumed — Step 7.
- ELK / Jaeger / SBA are wired infrastructure-wise but no logs are flowing yet (services log to stdout); the dual-appender + MDC discipline lights up in Step 11.

## Deviations from the original plan (recorded for future steps)

These came up during Step 1 implementation; each is small, isolated, and called out so a fresh session knows what's load-bearing.

- **api-service uses `spring-boot-starter-web` (MVC)**, not WebFlux as D5 specified. Reason: Spring Boot Admin 4.0.0's auto-configured `RegistrationClient` requires `RestTemplateBuilder` and refuses to start under pure WebFlux. Step 4 must either reintroduce WebFlux + provide an explicit `ReactiveRegistrationClient`, or stay on MVC and use `SseEmitter` for chat streaming. (Carry-over note already in `docs/steps/04-api-service-chat.md`.)
- **Docker builder base `eclipse-temurin:21-jdk`** for service images, not `:25-jdk`. Reason: Gradle 8.14.3's bundled Groovy can't re-parse Java 25 bytecode (`major version 69`) when build-logic compiles to Java 25. The toolchain still provisions Java 25 via Foojay during the build; the runtime stage stays on `eclipse-temurin:25-jre`. Service jars run on Java 25.
- **Per-service Gradle cache id** in each Dockerfile (`--mount=type=cache,id=gradle-<service>,target=/root/.gradle`). Without per-service ids, the four parallel docker builds collide on `/root/.gradle/caches/journal-1/journal-1.lock`.
- **`build-logic` pins `sourceCompatibility = targetCompatibility = 17`.** Defensive belt-and-suspenders against the same major-version-69 issue.
- **`apt-get install curl` in every service runtime stage.** `eclipse-temurin:25-jre` is Ubuntu 24.04 minimal — no `wget` and no `curl`. Healthchecks all use `curl -fsS http://.../actuator/health | grep -q '"UP"'`.
- **Kafka image: `apache/kafka:3.8.0`** (Bitnami images are no longer published to Docker Hub free tier; the `bitnami/kafka:3.x` tag is gone). Env vars switched from `KAFKA_CFG_*` (Bitnami) to plain `KAFKA_*` (Apache). `CLUSTER_ID` is fixed.
- **Jaeger image: `jaegertracing/all-in-one:1.60`** — `:1.61` and `:1.62` are not on Docker Hub.
- **Logstash host port: 5044** (`5044:5000`). macOS reserves 5000 for AirPlay Receiver. In-network services still talk to `logstash:5000`; the host-side mapping is a debugging convenience only.
- **nginx listens on both IPv4 and IPv6** (`listen 80; listen [::]:80;`). Without IPv6, the alpine container's healthcheck `wget http://localhost/` resolves to `::1` first and gets `connection refused`.
- **nginx `/api/` proxy strips the prefix** via the trailing slash on `proxy_pass http://api_service/;`. The SSE-safe location uses `rewrite ^/api/(.*/stream)$ /$1 break;`-style routing. So `/api/actuator/health` correctly reaches `/actuator/health` on api-service.
- **Spring Boot version stayed at 4.0.0** (Java 25 toolchain works with this combo).

## Hand-off to next session

Open `docs/steps/02-shared-contracts.md`. Run the brainstorming skill against it with the user; produce its detailed plan; implement; mark Step 2 done in `CHANGELOG.md`.
