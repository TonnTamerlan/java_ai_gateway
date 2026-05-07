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

## Hand-off to next session

Open `docs/steps/02-shared-contracts.md`. Run the brainstorming skill against it with the user; produce its detailed plan; implement; mark Step 2 done in `CHANGELOG.md`.
