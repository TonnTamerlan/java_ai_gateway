# Typed Goose

AI-gateway study project for a meet-up — Spring Boot, Kafka, Postgres, React, ELK, Docker. Local only. See [`CLAUDE.md`](CLAUDE.md) and [`docs/`](docs/).

## How to launch

Prerequisites: Docker (with Compose v2), JDK 25, [pnpm](https://pnpm.io/) via corepack. First run builds images and pulls dependencies — expect 5–10 minutes.

1. **Set up the env file** (gitignored, holds the OpenAI key placeholder):

   ```bash
   cp infra/docker/.env.example infra/docker/.env
   # any non-empty value works for local development
   ```

2. **Bring up the full stack** (backend in containers + Vite hot-reload for the frontend):

   ```bash
   docker compose \
     -f infra/docker/docker-compose.yml \
     -f infra/docker/docker-compose.dev.yml \
     up --wait
   ```

3. **Open the app**: <http://localhost:5173>. Other useful endpoints once the stack is up:

   | Service | URL |
   | --- | --- |
   | Frontend (Vite dev server) | <http://localhost:5173> |
   | API Gateway (Spring Cloud Gateway) | <http://localhost:8080> |
   | Eureka dashboard | <http://localhost:8761> |
   | Spring Boot Admin | <http://localhost:9000> |
   | Kibana | <http://localhost:5601> |
   | Jaeger UI | <http://localhost:16686> |

4. **Stop and clean up**:

   ```bash
   docker compose -f infra/docker/docker-compose.yml down -v
   ```

### Backend-only build & tests

```bash
./gradlew build                              # everything
./gradlew :services:chat-service:test        # one module
./gradlew :services:api-gateway:bootJar      # boot jar for a service
```

### Frontend-only build & tests

```bash
pnpm -C apps/frontend install
pnpm -C apps/frontend dev      # native Vite dev server (proxies /api → localhost:8080)
pnpm -C apps/frontend test
pnpm -C apps/frontend build
```

When running `pnpm dev` natively while the backend runs in Docker, the Vite proxy points at `http://localhost:8080`. Inside the dev-compose container it instead reads `VITE_API_PROXY_TARGET=http://api-gateway:8080` (set in `docker-compose.dev.yml`).
