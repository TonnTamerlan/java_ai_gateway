# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
COPY apps/frontend/package.json apps/frontend/pnpm-lock.yaml* ./apps/frontend/
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    cd apps/frontend && pnpm install --frozen-lockfile || pnpm install
COPY apps/frontend ./apps/frontend
RUN cd apps/frontend && pnpm build

FROM nginx:alpine
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/apps/frontend/dist /usr/share/nginx/html
EXPOSE 80
