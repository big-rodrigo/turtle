# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Development mode (live reload, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=BookingResourceTest

# Run integration tests
./mvnw verify

# Package (output: target/quarkus-app/quarkus-run.jar)
./mvnw package
```

Start local dependencies (PostgreSQL + Mailpit) via Docker Compose:
```bash
docker compose up -d
```

Mailpit web UI for inspecting emails locally: http://localhost:8025

## Architecture

**Quarkus 3.31.1** REST microservice (Java 21) for a coaching booking platform.

**Domain packages** under `src/main/java/turtle/`:
- `auth/` — JWT login/register, BCrypt password hashing
- `user/` — `AppUser` entity with roles: `CLIENT`, `COACH`, `COACH_PENDING`, `ADMIN`
- `coach/` — `CoachProfile`, `Availability` (time slots), `CoachingService` (named services with optional extras), coach approval workflow
- `booking/` — `Booking` lifecycle (`PENDING → APPROVED/REJECTED/CANCELLED`)
- `chat/` — `ChatMessage` scoped to bookings
- `notification/` — Email (Quarkus Mailer) and SMS/WhatsApp (Evolution API REST client)
- `admin/` — Admin operations (approve/reject coaches)
- `common/` — `ExceptionMappers`, `ErrorResponse` (global error handling)

**Layered pattern per domain:**
1. `*Resource.java` — JAX-RS REST controller (`@Path`, `@GET`/`@POST`/etc., `@RolesAllowed`)
2. `*Service.java` — Business logic (`@ApplicationScoped`, `@Transactional`)
3. Entity classes — extend `PanacheEntityBase` (Active Record; static finders on the entity class)
4. `dto/` — Records for request/response bodies, validated with `@Valid`

**Coaching Services:** Coaches define named services (`CoachingService` entity) with a description and an optional list of extra services (self-referential ManyToMany via `service_extras`). Extras cannot themselves have extras (1 level max). Time windows are bound to a service via `service_id`. When clients book, they can select which extras to include (`booking_extras` join table). Managed via `CoachingServiceMgmtService` and `CoachingServiceResource` (`/coaches/{coachId}/services`).

**Event-driven notifications:** Services fire CDI events (`Event<T>`) after transactions. `BookingEventObserver` listens with `@Observes(during = TransactionPhase.AFTER_SUCCESS)` and triggers email + WhatsApp notifications without coupling services to notification logic.

**Security:** SmallRye JWT with PKCS#8 key pair. Resources use `@Authenticated` and `@RolesAllowed`. The current user's ID is read from `@Inject JsonWebToken jwt` → `jwt.getSubject()`.

**Database:** PostgreSQL, schema managed by Flyway migrations in `src/main/resources/db/migration/`. Quarkus Dev Services auto-provisions a PostgreSQL container during tests.

**OpenAPI:** Swagger UI at `/swagger-ui`, spec at `/openapi`. Resources are annotated with `@Tag`, `@Operation`, `@APIResponse`, `@SecurityRequirement("jwt")`.

## Key Configuration

`src/main/resources/application.properties` — datasource, JWT keys, SMTP, CORS, Evolution API.

Override with env vars for production:
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `EVOLUTION_API_URL`, `EVOLUTION_API_KEY`, `EVOLUTION_API_INSTANCE`
- `CORS_ORIGINS`

Test profile (`src/test/resources/application.properties`) uses mock mailer and Dev Services PostgreSQL.
