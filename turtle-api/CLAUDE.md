# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Development mode (live reload, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run all tests
./mvnw test

# Run integration tests
./mvnw verify

# Run a single test class
./mvnw test -Dtest=GreetingResourceTest

# Package (output: target/quarkus-app/quarkus-run.jar)
./mvnw package

# Build uber-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Build native executable
./mvnw package -Dnative
# or with container build (no local GraalVM needed)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Architecture

This is a **Quarkus 3.31.1** REST microservice using Java 21.

**Key patterns:**
- REST resources use `@Path` annotations (JAX-RS via `quarkus-rest`)
- Database entities extend `PanacheEntity` (Active Record pattern — entities manage their own persistence)
- Dependency injection via Quarkus Arc (CDI)
- Reactive capabilities available via Eclipse Vert.x (`quarkus-vertx`)

**Stack:**
- REST: `quarkus-rest` (Vert.x-backed Jakarta REST)
- ORM: `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql`
- DI: `quarkus-arc`
- Testing: JUnit 5 + REST-Assured

**Source layout:**
- `src/main/java/turtle/` — application code
- `src/test/java/turtle/` — unit tests (`*Test.java`), integration tests (`*IT.java`)
- `src/main/resources/application.properties` — Quarkus configuration (database URLs, ports, etc.)
- `src/main/docker/` — Dockerfiles for JVM, native, and legacy-jar packaging

**Database:** PostgreSQL. Connection properties go in `application.properties` under `quarkus.datasource.*`.

**Test separation:** `*Test.java` files run with Surefire (unit/component tests against a test profile); `*IT.java` files run with Failsafe during `mvn verify` (integration tests against a packaged artifact).
