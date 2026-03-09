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
- `booking/` — `Booking` lifecycle (`PENDING_PAYMENT → AWAITING_COACH → CONFIRMED/REJECTED/CANCELLED`), session resources/materials
  - `domain/` — `Booking`, `BookingMaterial`, `BookingStatus`, `service/BookingDomainService`; `event/BookingCreatedEvent`, `BookingConfirmedEvent`, `BookingRejectedEvent`, `BookingCancelledEvent`
  - `application/` — `BookingApplicationService`
  - `api/` — `BookingResource` + DTOs (`BookingResponse`, `CreateBookingRequest`, `BookingResourceRequest`, `BookingResourceResponse`)
- `payment/` — Payment lifecycle, MercadoPago Checkout Pro integration
  - `domain/` — `Payment`, `PaymentStatus`; value objects `CheckoutPreference`, `PaymentInfo`; `service/PaymentGateway` (interface/port); `event/PaymentApprovedEvent`
  - `application/` — `PaymentApplicationService`
  - `api/` — `PaymentResource` + DTOs (`PaymentPreferenceResponse`, `MercadoPagoWebhookPayload`)
- `conversation/` — (was: `chat/`) `ChatMessage` scoped to confirmed bookings
  - `domain/` — `ChatMessage`; `event/ChatMessageSentEvent`
  - `application/` — `ConversationApplicationService`
  - `api/` — `ConversationResource` + DTOs
- `administration/` — (was: `admin/`) Coach approval workflow
  - `application/` — `CoachApprovalService`
  - `api/` — `AdminResource` + DTOs
- `infrastructure/notification/` — (was: `notification/`) `DomainEventObserver`, `WhatsAppNotificationService`, `EmailNotificationService`, `EvolutionApiClient`
- `infrastructure/payment/` — `MercadoPagoGateway` (implements `PaymentGateway` via MP Java SDK 2.1.26)
- `shared/` — (was: `common/`) `ExceptionMappers`, `ErrorResponse`, `OpenApiConfig`; `domain/SocialLinkType` (shared enum used by both coach and client social links)

**DDD Layers:**
- `domain/` — Entities (Panache Active Record), value objects (enums), domain services (pure invariants, no I/O)
- `application/` — Application services: orchestrate use cases, own `@Transactional`, cross-context calls, fire CDI events
- `api/` — JAX-RS resources (`@Path`, `@RolesAllowed`) + request/response DTOs

**Domain services (pure logic, no persistence):**
- `PasswordHasher` — BCrypt hash/verify
- `TimeWindowDomainService` — validate window parameters (dates, times, slot fit)
- `CoachingServiceDomainService` — validate extras (1-level depth, ownership)
- `BookingDomainService` — validate slots (consecutive, same coach, not booked, future), assert PENDING_PAYMENT / AWAITING_COACH status

**Coach Profiles:** Coaches have enriched profiles (`CoachProfile` entity) with `description`, `specialty`, `pictureUrl`, `status` (PENDING → APPROVED → REJECTED), and `socialLinks` (one-to-many `CoachSocialLink`). Managed via `CoachProfileApplicationService`. Endpoints: `GET /coaches` (list approved), `GET /coaches/{id}` (public profile), `PUT /coaches/{id}/profile` (COACH self-update), `PUT /admin/coaches/{userId}/profile` (ADMIN update).

**Client Profiles:** Clients have enriched profiles (`ClientProfile` entity) with `description` and `socialLinks` (one-to-many `ClientSocialLink`). A blank `ClientProfile` is auto-created on CLIENT registration in `AuthApplicationService`. Managed via `ClientProfileApplicationService`. Endpoints: `GET /clients/{id}` (public profile), `PUT /clients/{id}/profile` (CLIENT self-update).

**Social Links:** Both coaches and clients support social links with `SocialLinkType` enum (shared in `turtle.shared.domain`): `INSTAGRAM`, `TWITTER`, `LINKEDIN`, `YOUTUBE`, `TIKTOK`, `FACEBOOK`, `CUSTOM`. Custom links include an optional `label`. Social links are fully replaced on each profile update.

**Coaching Services:** Coaches define named services (`CoachingService` entity) with a description and an optional list of extra services (self-referential ManyToMany via `service_extras`). Extras cannot themselves have extras (1 level max). Time windows are bound to a service via `service_id`. When clients book, they can select which extras to include (`booking_extras` join table). Managed via `CoachingServiceApplicationService` and `CoachingServiceResource` (`/coaches/{coachId}/services`).

**Payments (MercadoPago Checkout Pro):** Clients must pay before a coach confirms. The flow is:
1. Client creates booking → status `PENDING_PAYMENT`, slots are reserved
2. Client calls `POST /bookings/{id}/payment/preference` → receives `checkoutUrl` to redirect to MercadoPago
3. Client pays on MercadoPago's hosted checkout → MP sends a webhook to `POST /payments/webhook`
4. Webhook handler calls `PaymentGateway.getPayment()` to verify status; on approval → booking moves to `AWAITING_COACH`, fires `PaymentApprovedEvent` (notifies coach)
5. Coach calls `PATCH /bookings/{id}/confirm` → status `CONFIRMED`; chat unlocks
6. Coach can also `PATCH /bookings/{id}/reject` → status `REJECTED` + auto-refund via `PaymentGateway.refund()`
7. Client can cancel (`DELETE /bookings/{id}`) from `PENDING_PAYMENT` or `AWAITING_COACH`; if already paid, triggers auto-refund

**Free sessions:** If `TimeWindow.pricePerUnit` is null or zero, `POST /bookings/{id}/payment/preference` skips the checkout and immediately moves the booking to `AWAITING_COACH` (response has `free: true`).

**Pluggable gateway:** `PaymentGateway` is an interface in `payment/domain/service/`. `MercadoPagoGateway` (`infrastructure/payment/`) is the only current implementation. Add new gateways by implementing the interface and making it a CDI `@ApplicationScoped` bean.

**Payment entity:** `Payment` (one-to-one with `Booking`) stores `preferenceId`, `externalPaymentId` (MP's payment ID, populated after webhook), `status` (`PaymentStatus`), and `amount`. `BookingResponse` includes a `paymentStatus` field (nullable — null until a payment record exists).

**Event-driven notifications:** Application services fire CDI events (`Event<T>`) after transactions. `DomainEventObserver` listens with `@Observes(during = TransactionPhase.AFTER_SUCCESS)` and triggers email + WhatsApp notifications without coupling services to notification logic.

| Event | Recipient | Trigger |
|---|---|---|
| `BookingCreatedEvent` | Client | "complete your payment" |
| `PaymentApprovedEvent` | Coach | "new paid session, confirm your availability" |
| `BookingConfirmedEvent` | Client | "session confirmed!" |
| `BookingRejectedEvent` | Client | "session declined, refund initiated" |
| `BookingCancelledEvent` | Coach | "session cancelled by client" |
| `ChatMessageSentEvent` | Other participant | message preview |

**Booking Resources (Materials & Links):** Coaches can attach links and materials (e.g. Google Drive, YouTube videos) to bookings that are `AWAITING_COACH` or `CONFIRMED`. `BookingMaterial` entity stores `title`, `url` (validated), optional `description`, and `createdAt`. Resources are included in `BookingResponse` and accessible to both coach and client. Endpoints: `POST /bookings/{id}/resources` (COACH — add), `GET /bookings/{id}/resources` (COACH/CLIENT — list), `DELETE /bookings/{id}/resources/{resourceId}` (COACH — remove). Managed via `BookingApplicationService`.

**Accepted cross-context dependencies (Panache Active Record trade-off):**
- `booking.domain.Booking` ↔ `coaching.domain.Availability` (bidirectional JPA relationship)
- Application services may call entities from other contexts (e.g., `BookingApplicationService` loads `Availability`)
- `BookingApplicationService` injects `PaymentApplicationService` for refund on reject/cancel

**Security:** SmallRye JWT with PKCS#8 key pair. Resources use `@Authenticated` and `@RolesAllowed`. The current user's ID is read from `@Inject JsonWebToken jwt` → `jwt.getSubject()`. The webhook endpoint `POST /payments/webhook` is `@PermitAll` (public).

**Database:** PostgreSQL, schema managed by Flyway migrations in `src/main/resources/db/migration/`. Quarkus Dev Services auto-provisions a PostgreSQL container during tests.

**OpenAPI:** Swagger UI at `/swagger-ui`, spec at `/openapi`. Resources are annotated with `@Tag`, `@Operation`, `@APIResponse`, `@SecurityRequirement("jwt")`.

## Key Configuration

`src/main/resources/application.properties` — datasource, JWT keys, SMTP, CORS, Evolution API, MercadoPago.

Override with env vars for production:
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `EVOLUTION_API_URL`, `EVOLUTION_API_KEY`, `EVOLUTION_API_INSTANCE`
- `CORS_ORIGINS`
- `MERCADOPAGO_ACCESS_TOKEN` — MP access token (use `TEST-...` for sandbox)
- `MERCADOPAGO_NOTIFICATION_URL` — publicly accessible URL for MP webhooks (e.g. via ngrok in dev)
- `MERCADOPAGO_BACK_URL_SUCCESS`, `MERCADOPAGO_BACK_URL_FAILURE`, `MERCADOPAGO_BACK_URL_PENDING` — frontend URLs MP redirects to after checkout

Test profile (`src/test/resources/application.properties`) uses mock mailer and Dev Services PostgreSQL.
