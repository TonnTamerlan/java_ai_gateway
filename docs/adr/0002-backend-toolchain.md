# ADR 0002 — Backend toolchain

- **Status:** Accepted
- **Date:** 2026-05-08

## Context

Three Spring Boot services need a common build, test, and packaging story. The host machine runs Java 17; Java 25 must be available without a manual install.

## Decision

- **Java 25** via Gradle `JavaLanguageVersion.of(25)` toolchain (auto-provisioned by Foojay convention plugin).
- **Spring Boot 4.0.0** for all services.
- **Gradle 8.14.3** multi-project, **Groovy DSL** (`settings.gradle`, `build.gradle`).
- **`build-logic`** convention plugins: `goose.java-conventions`, `goose.spring-boot-conventions`, `goose.testing-conventions`. Per-service build files are 5–10 lines.
- **`gradle/libs.versions.toml`** is the single source of truth for versions.

## Consequences

- Foojay needs network access on first build to download JDK 25.
- Spring Boot Gradle plugin and `io.spring.dependency-management` apply only to application modules; library modules (`shared-contracts`, `provider-openai`) get only `goose.java-conventions`.
- `./gradlew build` is the canonical command; CI-style verification is identical to local.
