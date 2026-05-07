# ADR 0008 — Repo layout

- **Status:** Accepted
- **Date:** 2026-05-08

## Decision

```
/
├── settings.gradle, build.gradle, gradle.properties, gradle/, gradlew, gradlew.bat
├── build-logic/
├── services/
│   ├── shared-contracts/
│   ├── ai-gateway/{core,provider-openai}/
│   ├── api-service/
│   └── calculation-service/
├── apps/frontend/                    # Vite + React + Ant Design
├── infra/
│   ├── docker/                       # docker-compose.yml + dev override + .env.example
│   ├── nginx/                        # multi-stage Dockerfile + nginx.conf
│   ├── spring-boot-admin/            # tiny Spring Boot Admin server (Gradle subproject)
│   ├── postgres/, kafka/, elasticsearch/, kibana/, jaeger/, logstash/
└── docs/{adr,steps,diagrams,meetup}/
```

- Frontend served in production by a dedicated nginx container; in dev by Vite via `docker-compose.dev.yml`.
- Gradle paths use `:services:ai-gateway:ai-gateway-core` (renamed at settings level for unique short names).

## Consequences

- One `docker compose up` from `infra/docker/` boots everything. Build context is the repo root.
- `infra/spring-boot-admin` is a Gradle subproject by virtue of Java code — its location next to the other infra is a deliberate choice to keep "infra-shaped" components together.
- Adding a new service = `services/<name>/` + entry in `settings.gradle` + entry in `docker-compose.yml`.
