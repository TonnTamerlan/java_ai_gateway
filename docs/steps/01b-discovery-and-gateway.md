# Step 01b — Discovery + edge Spring Cloud Gateway + chat-service split

**Status:** Done — 2026-05-08 (architecture pivot, recorded in ADR D10).

## What this step lands

- New `infra/eureka-server` module — Netflix Eureka (port 8761).
- `services/api-service/` renamed to `services/api-gateway/`. The module becomes a pure Spring Cloud Gateway edge router (Eureka client).
  - Routes: `/api/chats/**` → `lb://chat-service`, `/api/jobs/**` → `lb://calculation-service`. Both apply `StripPrefix=1`.
  - `/api/health` returns a static `{"status":"UP"}` via `RouterFunction` so the existing frontend health probe keeps working.
- New `services/chat-service` skeleton (WebFlux, Eureka client, port 8083). No chat code yet.
- `services/ai-gateway/core` and `services/calculation-service`: drop `spring-boot-admin-starter-client`, add `spring-cloud-starter-netflix-eureka-client`.
- `infra/spring-boot-admin`: gains `spring-cloud-starter-netflix-eureka-client` and `@EnableDiscoveryClient`. Discovers monitored services via Eureka.
- `infra/nginx/nginx.conf`: upstream rename `api_service` → `api_gateway`; `proxy_pass` no longer strips `/api/` prefix.
- `infra/docker/docker-compose.yml`: adds `eureka-server` and `chat-service`; renames `api-service` → `api-gateway`; switches every Java service's `depends_on` to `eureka-server`.
- `gradle/libs.versions.toml`: adds Spring Cloud BOM + four starters.
- `build-logic/src/main/groovy/goose.spring-boot-conventions.gradle`: imports the Spring Cloud BOM via `dependencyManagement`.
- ADR D10 records the change.

## Verify

```bash
./gradlew clean build          # all subprojects compile + contextLoads tests pass
pnpm -C apps/frontend test
pnpm -C apps/frontend build

cp infra/docker/.env.example infra/docker/.env  # if not already present
docker compose -f infra/docker/docker-compose.yml up --wait

# 1. Eureka registry lists every Java app
curl -fsS http://localhost:8761/eureka/apps

# 2. nginx → api-gateway → static health response
curl -fsS http://localhost/api/health

# 3. SCG routing reaches downstream (404 expected at this stage,
#    presence of routing in api-gateway logs proves the path)
curl -i http://localhost/api/chats/anything
curl -i http://localhost/api/jobs/anything

# 4. SBA reads from Eureka instead of being pushed to
curl -fsS http://localhost:9000/applications

docker compose -f infra/docker/docker-compose.yml down -v
```

## Out of scope (deferred)

- Real chat persistence (Flyway + `chat` schema) — lands in the chat-service step that introduces persistence (Step 4-equivalent).
- Real `OpenAiChatProvider` / `OpenAiBatchProvider` in ai-gateway — Steps 3 / 9.
- Real Kafka topics + envelopes — Step 7.
- SSE relay code in chat-service — Step 4-equivalent.
- TLS, auth, secrets management — out of project scope (local-only meet-up demo).

## Hand-off to next session

Open `docs/steps/02-shared-contracts.md` and proceed with Step 2 against the post-pivot topology.
