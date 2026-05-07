# CLAUDE.md — operational guide for Claude Code

This is **not** a user manual. It tells future Claude Code sessions how to work in this repo.

## Project

**Typed Goose** — AI-gateway study project for a meet-up. Local-only, no security.

## Where things live

| Path | What |
| --- | --- |
| `services/shared-contracts/` | Vendor-agnostic domain interfaces + Kafka envelopes. Empty in Step 1. |
| `services/ai-gateway/{core,provider-openai}/` | AI Gateway and its OpenAI provider. |
| `services/api-service/` | Frontend BFF (WebFlux). |
| `services/calculation-service/` | Async heavy-work emulator. |
| `infra/spring-boot-admin/` | Tiny Spring Boot Admin server (Gradle subproject). |
| `apps/frontend/` | Vite + React + TS + Ant Design. |
| `infra/docker/` | `docker-compose.yml` + dev override + `.env.example`. |
| `infra/{nginx,postgres,kafka,elasticsearch,kibana,jaeger,logstash}/` | Per-piece infra config. |
| `build-logic/` | Groovy convention plugins (`goose.java-conventions`, `goose.spring-boot-conventions`, `goose.testing-conventions`). |
| `gradle/libs.versions.toml` | Single source of truth for versions. |
| `docs/adr/` | One ADR per `D-decision`. Append; supersede; do not rewrite. |
| `docs/steps/` | Per-step plans + handoff. `CHANGELOG.md` is the cross-session log. |
| `docs/diagrams/` | `.mmd` mermaid sources. |

## Decisions

- **D1** Sync REST+SSE chat, async Kafka calc → [`docs/adr/0001-end-to-end-flow.md`](docs/adr/0001-end-to-end-flow.md)
- **D2** Java 25 + Spring Boot 4.0 + Gradle multi-project (Groovy DSL) → [`docs/adr/0002-backend-toolchain.md`](docs/adr/0002-backend-toolchain.md)
- **D3** ChatProvider/BatchProvider in shared-contracts; Spring AI (chat) + openai-java SDK (batch) → [`docs/adr/0003-ai-provider-abstraction.md`](docs/adr/0003-ai-provider-abstraction.md)
- **D4** Vite + React 19 + TS + Ant Design v5 + pnpm → [`docs/adr/0004-frontend-stack.md`](docs/adr/0004-frontend-stack.md)
- **D5** SSE for chat, polling for jobs; WebFlux throughout API Service → [`docs/adr/0005-push-channel.md`](docs/adr/0005-push-channel.md)
- **D6** Single Postgres, three logical schemas (`app`/`calc`/`ai`), per-service Flyway → [`docs/adr/0006-postgres-data-model.md`](docs/adr/0006-postgres-data-model.md)
- **D7** ES + Kibana + Logstash (TCP appender) + dual file appender; OTel → Jaeger; SBA → [`docs/adr/0007-elk-and-observability.md`](docs/adr/0007-elk-and-observability.md)
- **D8** `services/* + apps/* + infra/* + docs/*` layout, dedicated nginx → [`docs/adr/0008-repo-layout.md`](docs/adr/0008-repo-layout.md)
- **D9** Layered tests (unit / slice / Testcontainers / Pact); JaCoCo 80/75 from Step 6 → [`docs/adr/0009-test-strategy.md`](docs/adr/0009-test-strategy.md)

## Build / run commands

```bash
./gradlew build
./gradlew :services:api-service:test
./gradlew :services:ai-gateway:ai-gateway-core:bootJar

pnpm -C apps/frontend install
pnpm -C apps/frontend test
pnpm -C apps/frontend build

# Single command — first run downloads/builds everything, ~5–10 min.
docker compose -f infra/docker/docker-compose.yml up --wait
docker compose -f infra/docker/docker-compose.yml down -v

# Hot-reload frontend (backend stays containerised):
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.dev.yml up
```

Local copy of `infra/docker/.env` is required (any value for `OPENAI_API_KEY`); `.env.example` is the template. `infra/docker/.env` is gitignored.

## Conventions

- Gradle build files use **Groovy DSL** (`build.gradle`, `settings.gradle`). Convention plugins live in `build-logic/src/main/groovy/`.
- New library / plugin versions go in `gradle/libs.versions.toml` first. Build files reference the catalog; never bare versions.
- **Do not pre-create DB schemas / tables** — each service introduces its own Flyway migrations the first session it has work to do.
- **Do not pre-create Kafka topics or envelopes** until Step 2 / Step 7.
- **Do not introduce Pact** until Step 12.
- TDD is rigid for unit + Spring slice layers; flexible for integration / e2e.
- JSON logging only (no `System.out.println`).
- Per-service code stays inside its own module. Cross-cutting types go to `services/shared-contracts` — vendor-pure (no Spring AI, no SDK imports).

## Cross-session workflow

1. Open the lowest-numbered un-completed `docs/steps/NN-*.md`.
2. Run the brainstorming skill on it with the user; produce that step's detailed plan.
3. Implement. Keep `./gradlew build`, `pnpm -C apps/frontend test+build`, and `docker compose up --wait` green at every commit.
4. Update the step file with what changed; append a one-line entry to `docs/steps/CHANGELOG.md`.
5. Mark step done. **Do not start a later step in the same session.**

## What is intentionally NOT here yet (Step 1)

| Gap | Lands in |
| --- | --- |
| Domain interfaces / Kafka envelopes | Step 2 |
| Any OpenAI call (real or mocked) | Step 3 |
| Any DB schema, table, or Flyway migration | Step 4 |
| Kafka topic creation **and** use | Step 7 |
| OpenAI Batch API + `batch_correlations` | Step 9 |
| Real ELK indexing, traces, Kibana dashboards beyond "service appears" | Step 11 |
| Pact | Step 12 |

## Anti-patterns to avoid

- Don't bake business logic into `shared-contracts`.
- Don't add starters/dependencies outside the version catalog.
- Don't write code without first updating or creating the relevant `docs/steps/NN-*.md`.
- Don't change ADRs to reflect new decisions — append a new ADR that supersedes the old one.
- Don't apply `org.springframework.boot` plugin to library modules (it tries to package as a Boot jar).
- Don't run pnpm via npm / yarn — corepack pins pnpm 9.15.0 via `packageManager` field.
