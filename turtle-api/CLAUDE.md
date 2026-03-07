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

**DDD structure** under `src/main/java/turtle/` — organized into bounded contexts with `domain/`, `application/`, and `api/` layers:

- `identity/` — (was: `auth/` + `user/`) User registration, JWT authentication, BCrypt via `PasswordHasher`; client profile management
  - `domain/` — `AppUser`, `UserRole`, `ClientProfile`, `ClientSocialLink`; `service/PasswordHasher`
  - `application/` — `AuthApplicationService`, `ClientProfileApplicationService`
  - `api/` — `AuthResource`, `ClientResource` + DTOs
- `coaching/` — (was: `coach/`) Coach profiles, availability, time windows, coaching services
  - `domain/` — `CoachProfile`, `CoachStatus`, `CoachSocialLink`, `Availability`, `AvailabilityStatus`, `TimeWindow`, `CoachingService`; `service/TimeWindowDomainService`, `service/CoachingServiceDomainService`
  - `application/` — `CoachQueryService`, `CoachProfileApplicationService`, `TimeWindowApplicationService`, `CoachingServiceApplicationService`
  - `api/` — `CoachResource`, `CoachingServiceResource` + DTOs
- `booking/` — `Booking` lifecycle (`PENDING → APPROVED/REJECTED/CANCELLED`)
  - `domain/` — `Booking`, `BookingStatus`, `service/BookingDomainService`; `event/BookingCreatedEvent`, `BookingApprovedEvent`, `BookingRejectedEvent`
  - `application/` — `BookingApplicationService`
  - `api/` — `BookingResource` + DTOs
- `conversation/` — (was: `chat/`) `ChatMessage` scoped to approved bookings
  - `domain/` — `ChatMessage`; `event/ChatMessageSentEvent`
  - `application/` — `ConversationApplicationService`
  - `api/` — `ConversationResource` + DTOs
- `administration/` — (was: `admin/`) Coach approval workflow
  - `application/` — `CoachApprovalService`
  - `api/` — `AdminResource` + DTOs
- `infrastructure/notification/` — (was: `notification/`) `DomainEventObserver`, `WhatsAppNotificationService`, `EmailNotificationService`, `EvolutionApiClient`
- `shared/` — (was: `common/`) `ExceptionMappers`, `ErrorResponse`, `OpenApiConfig`; `domain/SocialLinkType` (shared enum used by both coach and client social links)

**DDD Layers:**
- `domain/` — Entities (Panache Active Record), value objects (enums), domain services (pure invariants, no I/O)
- `application/` — Application services: orchestrate use cases, own `@Transactional`, cross-context calls, fire CDI events
- `api/` — JAX-RS resources (`@Path`, `@RolesAllowed`) + request/response DTOs

**Domain services (pure logic, no persistence):**
- `PasswordHasher` — BCrypt hash/verify
- `TimeWindowDomainService` — validate window parameters (dates, times, slot fit)
- `CoachingServiceDomainService` — validate extras (1-level depth, ownership)
- `BookingDomainService` — validate slots (consecutive, same coach, not booked, future), assert PENDING status

**Coach Profiles:** Coaches have enriched profiles (`CoachProfile` entity) with `description`, `specialty`, `pictureUrl`, `status` (PENDING → APPROVED → REJECTED), and `socialLinks` (one-to-many `CoachSocialLink`). Managed via `CoachProfileApplicationService`. Endpoints: `GET /coaches` (list approved), `GET /coaches/{id}` (public profile), `PUT /coaches/{id}/profile` (COACH self-update), `PUT /admin/coaches/{userId}/profile` (ADMIN update).

**Client Profiles:** Clients have enriched profiles (`ClientProfile` entity) with `description` and `socialLinks` (one-to-many `ClientSocialLink`). A blank `ClientProfile` is auto-created on CLIENT registration in `AuthApplicationService`. Managed via `ClientProfileApplicationService`. Endpoints: `GET /clients/{id}` (public profile), `PUT /clients/{id}/profile` (CLIENT self-update).

**Social Links:** Both coaches and clients support social links with `SocialLinkType` enum (shared in `turtle.shared.domain`): `INSTAGRAM`, `TWITTER`, `LINKEDIN`, `YOUTUBE`, `TIKTOK`, `FACEBOOK`, `CUSTOM`. Custom links include an optional `label`. Social links are fully replaced on each profile update.

**Coaching Services:** Coaches define named services (`CoachingService` entity) with a description and an optional list of extra services (self-referential ManyToMany via `service_extras`). Extras cannot themselves have extras (1 level max). Time windows are bound to a service via `service_id`. When clients book, they can select which extras to include (`booking_extras` join table). Managed via `CoachingServiceApplicationService` and `CoachingServiceResource` (`/coaches/{coachId}/services`).

**Event-driven notifications:** Application services fire CDI events (`Event<T>`) after transactions. `DomainEventObserver` listens with `@Observes(during = TransactionPhase.AFTER_SUCCESS)` and triggers email + WhatsApp notifications without coupling services to notification logic.

**Accepted cross-context dependencies (Panache Active Record trade-off):**
- `booking.domain.Booking` ↔ `coaching.domain.Availability` (bidirectional JPA relationship)
- Application services may call entities from other contexts (e.g., `BookingApplicationService` loads `Availability`)

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
