# ADR 0010 — Discovery + edge Spring Cloud Gateway + chat-service split

- **Status:** Accepted
- **Date:** 2026-05-08
- **Supersedes (in part):** ADR 0005 (the "WebFlux throughout API Service" clause)
- **Amends:** ADR 0001 (chat path), ADR 0006 (schemas), ADR 0008 (modules)

## Context

Step 1 wired the FE through nginx directly to a Spring **MVC** `api-service` shell that registered to Spring Boot Admin via SBA's *client* starter (push-registration). ADR D5 mandated WebFlux throughout `api-service` for the upcoming SSE chat endpoint, but Step 1 had to fall back to MVC because Spring Boot Admin 4.0.0's auto-configured `RegistrationClient` requires `RestTemplateBuilder` and won't start on pure WebFlux. Step 4 was carrying that compatibility blocker forward.

While reviewing the topology before Step 2 starts, the responsibilities of `api-service` were re-considered. It was set to host both edge routing *and* the chat BFF *and* the jobs BFF in a single WebFlux process. That conflates three concerns at one address. The pivot below splits them up and introduces a service registry so Spring Boot Admin can read instances from discovery instead of being pushed to — which removes the WebFlux/SBA blocker entirely.

## Decision

1. **Service discovery via Netflix Eureka.** New module `infra/eureka-server` (Spring Boot app, `@EnableEurekaServer`, port 8761). Every Java service registers as a Eureka client.
2. **`services/api-service/` → `services/api-gateway/`.** Renamed at the directory, Gradle module, Java package (`com.typedgoose.api` → `com.typedgoose.gateway`), Spring application name (`api-service` → `api-gateway`), and Docker compose service-name level. The module is now a **pure Spring Cloud Gateway** edge router (reactive, WebFlux). Routes:
   - `Path=/api/chats/**` → `lb://chat-service` (`StripPrefix=1`)
   - `Path=/api/jobs/**` → `lb://calculation-service` (`StripPrefix=1`)
   - `/api/health` returns a static `{"status":"UP"}` via a small `RouterFunction` so the existing frontend health probe keeps working.
3. **New `services/chat-service/`** owns chat REST + SSE relay. SCG routes `/api/chats/**` to it. chat-service is the only service that calls ai-gateway over REST/SSE for chat traffic. Persists in the `chat` Postgres schema (Flyway lands when chat actually persists, deferred).
4. **`calculation-service`** gains its own REST surface (jobs CRUD/poll). SCG routes `/api/jobs/**` to it. Async Kafka request/reply with ai-gateway is unchanged. Persists in the `calc` schema.
5. **`ai-gateway` is stateless** — pure router/proxy to AI providers and models. No Postgres schema, no DB deps. Inbound only from chat-service (REST) and calculation-service (Kafka). Never reachable from the FE.
6. **Spring Boot Admin via discovery.** Drops `spring-boot-admin-starter-client` from every service. SBA itself becomes a Eureka client and discovers monitored services via the registry.
7. **nginx** keeps its job: SPA on `/`, `/api/*` proxied to `api-gateway`. Two small nginx changes: upstream rename `api_service` → `api_gateway`, and `proxy_pass` no longer strips the `/api/` prefix (SCG sees the full path so its `Path=/api/...` predicates match).
8. **Postgres schema ownership.** D6's three-schema layout (`app`/`calc`/`ai`) is replaced by two: `chat` (chat-service) and `calc` (calculation-service). `ai-gateway` has no schema (per #5). `api-gateway` has no DB at all.

This pivot supersedes the "API Service runs WebFlux throughout" clause of D5 — that runtime concern is now per-service: api-gateway and chat-service are WebFlux; calculation-service stays MVC for its REST surface.

## Topology

```
Browser
  └─ nginx :80
       ├─ /            → static SPA
       └─ /api/*       → api-gateway :8080 (Spring Cloud Gateway, WebFlux, Eureka client)
                          ├─ /api/chats/**  → lb://chat-service
                          └─ /api/jobs/**   → lb://calculation-service

chat-service :8083 (WebFlux, Eureka client) → ai-gateway via REST/SSE
calculation-service :8082 (Eureka client)   → ai-gateway via Kafka request/reply
ai-gateway :8081 (stateless, Eureka client)
eureka-server :8761
spring-boot-admin :9000 (Eureka discovery client)
```

## Consequences

- The Step 4 carry-over note ("api-service uses MVC because SBA push-registration needs RestTemplateBuilder") is obsolete. SBA now reads instances from Eureka; the WebFlux/SBA conflict goes away.
- Adding a new service is now: `services/<name>/` + `settings.gradle` entry + Docker Compose entry + Eureka client config in `application.yml`. The service appears in SBA automatically once registered.
- One additional container (`eureka-server`) starts up first in compose ordering; every Java service's `depends_on` switches from SBA to Eureka.
- Routing logic lives in one place (api-gateway). Downstream services don't see `/api/` prefixes — SCG strips them. This isolates each service's URL surface from the edge.
- `shared-contracts` remains vendor-pure. ai-gateway's role as a pure provider router is reinforced (no schema, no FE-facing endpoints).
