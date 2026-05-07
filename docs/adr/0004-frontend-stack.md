# ADR 0004 — Frontend stack

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

Frontend hosts a chat panel and a calculation dashboard. Should look "enterprise admin", be quick to scaffold, and demo well.

## Decision

- **Vite + React 19 + TypeScript** with **pnpm 9** (corepack-managed).
- **Ant Design v5** as the component library — Layout, Card, Tag, Form, Table, Drawer, Steps cover both UIs.
- **TanStack Query** for server state; **react-hook-form + zod** for the calc form (introduced in Step 5/10).
- Native **`EventSource`** for SSE consumption.
- **Vitest 3 + React Testing Library 16** for unit/component tests; **Playwright** for one e2e per major flow (Step 6 / Step 10).
- Served in production by a dedicated nginx container (multi-stage build: node → nginx). Vite dev server available via the dev compose override.

## Consequences

- Bundle includes AntD v5 — large but acceptable; chunking optimisation can wait until Step 13.
- Ant Design v5 needs React 19-compatible peer (>= 5.21).
- Frontend lives in `apps/frontend`; pnpm builds it independently of Gradle.
