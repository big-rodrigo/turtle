# Coaching Scheduling App — Implementation Plan

## Overview

Build a REST API on top of the existing Quarkus 3.31.1/PostgreSQL scaffold that lets clients book coaching sessions, coaches approve or reject them, and both sides chat after approval. Every booking state change triggers a WhatsApp notification via the Evolution API.

---

## Current State Analysis

- Single placeholder endpoint (`GET /hello`) and one unused JPA entity (`MyEntity`).
- PostgreSQL driver, Hibernate ORM Panache, and Vert.x are already wired in but nothing persists yet.
- `application.properties` is empty; no security, no JSON, no HTTP client extension.
- No migration tooling yet — DDL would currently be managed by Hibernate's `drop-and-create` dev mode default.

## Desired End State

A running REST API reachable at `http://localhost:8080` where:
- A client can register, log in, browse coach availability, and book a session.
- A coach can log in, view pending bookings, and approve or reject them.
- After approval, both sides can exchange messages in a per-booking chat thread.
- Every status change (booking created, approved, rejected) and every new chat message sends a WhatsApp notification to the relevant party through Evolution API.

Verified by: all unit tests pass (`./mvnw test`), manual Postman/curl flows for happy path and error paths work as described in Phase success criteria.

### Key Discoveries
- `quarkus-rest-jackson` is not in pom.xml — JSON responses will silently fail without it.
- `quarkus-smallrye-jwt` + `quarkus-security` needed for JWT auth; not present.
- `quarkus-flyway` needed for reproducible schema migrations; not present.
- Quarkus REST client (`quarkus-rest-client-reactive`) needed to call Evolution API.
- Evolution API text message endpoint: `POST /message/sendText/{instanceName}` with header `apikey: <key>` and body `{"number":"55...","text":"..."}`.
- `MyEntity` and `GreetingResource` are pure scaffolding — delete them in Phase 1.

## What We're NOT Doing

- No frontend / UI.
- No OAuth / social login — plain email + password with BCrypt + JWT.
- No file or media uploads.
- No recurring availability rules (coaches set concrete date+time slots only).
- No payment processing.
- No inbound WhatsApp message handling (webhooks from Evolution API).
- No real-time WebSocket/SSE chat — polling only for now.
- No rate limiting or abuse protection.
- No email notifications — WhatsApp only.

---

## Implementation Approach

Work bottom-up: dependencies → schema → entities → auth → business logic → notifications → chat. Each phase leaves the app in a compilable, testable state. Notifications are a side-effect service injected into existing services (CDI `@Observes` or direct call), so they never block the main flows.

---

## Phase 1: Project Foundation

### Overview
Add missing Quarkus extensions, establish package structure, configure properties, and delete placeholder files.

### Changes Required

#### 1. pom.xml — add extensions
**File**: `pom.xml`

```xml
<!-- JSON support -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>

<!-- Security + JWT -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt-build</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-security</artifactId>
</dependency>

<!-- Database migrations -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>

<!-- HTTP client for Evolution API -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-client-reactive-jackson</artifactId>
</dependency>

<!-- BCrypt for password hashing -->
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>
```

#### 2. application.properties — baseline config
**File**: `src/main/resources/application.properties`

```properties
# Datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USER:turtle}
quarkus.datasource.password=${DB_PASSWORD:turtle}
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:turtle_db}

# Hibernate — let Flyway own the schema; Hibernate just validates
quarkus.hibernate-orm.database.generation=validate
quarkus.flyway.migrate-at-start=true

# JWT
mp.jwt.verify.issuer=turtle-api
quarkus.smallrye-jwt.new-token.issuer=turtle-api
quarkus.smallrye-jwt.new-token.lifespan=86400

# Generate keypair for dev (DO NOT use in prod — set smallrye.jwt.sign.key.location externally)
smallrye.jwt.sign.key.location=privateKey.pem
mp.jwt.verify.publickey.location=publicKey.pem

# Evolution API
evolution.api.base-url=${EVOLUTION_API_URL:http://localhost:8080}
evolution.api.key=${EVOLUTION_API_KEY:changeme}
evolution.api.instance=${EVOLUTION_API_INSTANCE:turtle}
```

#### 3. Package structure to create
```
src/main/java/turtle/
├── auth/
│   ├── AuthResource.java
│   ├── AuthService.java
│   └── dto/  (LoginRequest, RegisterRequest, TokenResponse)
├── user/
│   └── AppUser.java          (entity)
├── coach/
│   ├── CoachProfile.java     (entity)
│   ├── Availability.java     (entity)
│   ├── CoachResource.java
│   └── CoachService.java
├── booking/
│   ├── Booking.java          (entity)
│   ├── BookingStatus.java    (enum)
│   ├── BookingResource.java
│   ├── BookingService.java
│   └── dto/
├── chat/
│   ├── ChatMessage.java      (entity)
│   ├── ChatResource.java
│   └── dto/
└── notification/
    ├── EvolutionApiClient.java   (REST client interface)
    └── NotificationService.java
```

#### 4. Delete placeholder files
- `src/main/java/turtle/GreetingResource.java`
- `src/main/java/turtle/MyEntity.java`
- `src/test/java/turtle/GreetingResourceTest.java`
- `src/test/java/turtle/GreetingResourceIT.java`

### Success Criteria

#### Automated Verification
- [ ] `./mvnw compile` passes with no errors after adding extensions.

---

## Phase 2: Database Schema (Flyway)

### Overview
Create all tables via Flyway migration so the schema is version-controlled and reproducible.

### Changes Required

#### 1. Migration file
**File**: `src/main/resources/db/migration/V1__initial_schema.sql`

```sql
CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    email       VARCHAR(200)  NOT NULL UNIQUE,
    phone       VARCHAR(30),                       -- WhatsApp number e.g. 5511999999999
    password_hash VARCHAR(72) NOT NULL,
    role        VARCHAR(20)   NOT NULL              -- CLIENT | COACH
);

CREATE TABLE coach_profile (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL UNIQUE REFERENCES app_user(id),
    bio         TEXT,
    specialty   VARCHAR(200)
);

CREATE TABLE availability (
    id          BIGSERIAL PRIMARY KEY,
    coach_id    BIGINT        NOT NULL REFERENCES app_user(id),
    starts_at   TIMESTAMP     NOT NULL,
    ends_at     TIMESTAMP     NOT NULL,
    booked      BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE booking (
    id          BIGSERIAL PRIMARY KEY,
    client_id   BIGINT        NOT NULL REFERENCES app_user(id),
    coach_id    BIGINT        NOT NULL REFERENCES app_user(id),
    availability_id BIGINT    NOT NULL UNIQUE REFERENCES availability(id),
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | CANCELLED
    notes       TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_message (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT        NOT NULL REFERENCES booking(id),
    sender_id   BIGINT        NOT NULL REFERENCES app_user(id),
    content     TEXT          NOT NULL,
    sent_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

#### 2. Entities — one per table
Each entity extends `PanacheEntityBase` (using its own `@Id` field) or `PanacheEntity`.

**`AppUser`** (`turtle/user/AppUser.java`):
```java
@Entity @Table(name = "app_user")
public class AppUser extends PanacheEntity {
    public String name;
    public String email;
    public String phone;
    @Column(name = "password_hash") public String passwordHash;
    @Enumerated(EnumType.STRING) public UserRole role;

    public static Optional<AppUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
```

**`Availability`**: fields `coach` (`@ManyToOne AppUser`), `startsAt`, `endsAt`, `booked`.

**`Booking`**: fields `client`, `coach`, `availability` (`@OneToOne`), `status` (`BookingStatus enum`), `notes`, `createdAt`.

**`ChatMessage`**: fields `booking` (`@ManyToOne`), `sender` (`@ManyToOne AppUser`), `content`, `sentAt`.

### Success Criteria

#### Automated Verification
- [ ] `./mvnw quarkus:dev` starts without Hibernate validation errors.
- [ ] Flyway reports migration applied: log line `Successfully applied 1 migration`.

---

## Phase 3: Authentication

### Overview
Register and login endpoints returning a JWT. All subsequent endpoints use `@RolesAllowed`.

### Changes Required

#### 1. AuthService
**File**: `src/main/java/turtle/auth/AuthService.java`

Key methods:
- `register(RegisterRequest req)` — hash password with BCrypt, persist `AppUser`, return `TokenResponse`.
- `login(LoginRequest req)` — verify BCrypt hash, build JWT via `io.smallrye.jwt.build.Jwt.issuer(...).subject(...).groups(role).sign()`, return token.

#### 2. AuthResource
**File**: `src/main/java/turtle/auth/AuthResource.java`

```
POST /auth/register  — body: {name, email, phone, password, role}  → 201 {token}
POST /auth/login     — body: {email, password}                      → 200 {token}
```

No `@Authenticated` on these endpoints.

#### 3. JWT key generation for dev
Add to `pom.xml` exec-maven-plugin or document that running `openssl genrsa -out privateKey.pem 2048 && openssl rsa -in privateKey.pem -pubout -out publicKey.pem` is a one-time dev setup step. Keys live at project root (already in `.gitignore`).

### Success Criteria

#### Automated Verification
- [ ] `./mvnw test -Dtest=AuthResourceTest` passes (test: register → login → JWT is valid).

#### Manual Verification
- [ ] `POST /auth/register` returns 201 with token for both CLIENT and COACH roles.
- [ ] `POST /auth/login` with wrong password returns 401.
- [ ] JWT decoded at jwt.io shows correct `groups` claim.

---

## Phase 4: Coach Availability Management

### Overview
Coaches publish concrete available time slots; clients can browse them.

### Changes Required

#### 1. CoachResource
**File**: `src/main/java/turtle/coach/CoachResource.java`

```
GET  /coaches                          → list of {id, name, specialty} (public)
GET  /coaches/{id}/availability        → list of free slots (public)
POST /coaches/{id}/availability        @RolesAllowed("COACH") — body: {startsAt, endsAt}
DELETE /coaches/availability/{slotId}  @RolesAllowed("COACH") — delete own unbooked slot
```

`POST` must assert `id` matches JWT subject to prevent a coach editing another's slots.

#### 2. CoachService
- `addSlot(Long coachId, LocalDateTime starts, LocalDateTime ends)` — validates `ends > starts`, no overlap with existing slots for that coach, persists `Availability`.
- `listFreeSlots(Long coachId)` — returns slots where `booked = false` and `startsAt > now()`.

### Success Criteria

#### Automated Verification
- [ ] `./mvnw test -Dtest=CoachResourceTest` passes.

#### Manual Verification
- [ ] Coach can add a slot; it appears in `GET /coaches/{id}/availability`.
- [ ] Adding an overlapping slot returns 409.
- [ ] CLIENT JWT cannot call `POST /coaches/{id}/availability` (returns 403).

---

## Phase 5: Booking Flow

### Overview
Core business logic: client books a slot, coach approves or rejects, notifications fire.

### Changes Required

#### 1. BookingResource
**File**: `src/main/java/turtle/booking/BookingResource.java`

```
POST   /bookings                       @RolesAllowed("CLIENT") — body: {availabilityId, notes}
GET    /bookings                       @Authenticated — own bookings (client sees theirs; coach sees theirs)
GET    /bookings/{id}                  @Authenticated — must own booking
PATCH  /bookings/{id}/approve          @RolesAllowed("COACH")
PATCH  /bookings/{id}/reject           @RolesAllowed("COACH")
DELETE /bookings/{id}                  @Authenticated — cancel (only if PENDING, only by owner client)
```

#### 2. BookingService
**File**: `src/main/java/turtle/booking/BookingService.java`

Key methods:
- `create(Long clientId, Long availabilityId, String notes)`:
  1. Load `Availability`; assert `booked = false`.
  2. Set `availability.booked = true` atomically (optimistic locking or `@Transactional`).
  3. Persist `Booking` with status `PENDING`.
  4. Fire `BookingCreatedEvent` (CDI event).
- `approve(Long bookingId, Long coachId)`:
  1. Assert booking belongs to `coachId` and status is `PENDING`.
  2. Set status to `APPROVED`.
  3. Fire `BookingApprovedEvent`.
- `reject(Long bookingId, Long coachId)`:
  1. Assert ownership + `PENDING`.
  2. Set status `REJECTED`, set `availability.booked = false` (slot freed).
  3. Fire `BookingRejectedEvent`.

#### 3. CDI Event classes (simple POJOs in `booking/event/`)
- `BookingCreatedEvent(Booking booking)`
- `BookingApprovedEvent(Booking booking)`
- `BookingRejectedEvent(Booking booking)`

### Success Criteria

#### Automated Verification
- [ ] `./mvnw test -Dtest=BookingResourceTest` passes.
- [ ] Booking a slot that is already `booked = true` returns 409.
- [ ] Non-owner coach cannot approve another coach's booking (returns 403).

#### Manual Verification
- [ ] Full happy path: register client, register coach, coach adds slot, client books it, booking appears as PENDING, coach approves, booking appears as APPROVED.
- [ ] Rejected booking frees the slot (slot reappears in `GET /coaches/{id}/availability`).

**Pause here for manual confirmation before proceeding to Phase 6.**

---

## Phase 6: Notification Module (Evolution API)

### Overview
Send WhatsApp messages to the relevant party on every booking state transition and new chat message.

### Changes Required

#### 1. EvolutionApiClient
**File**: `src/main/java/turtle/notification/EvolutionApiClient.java`

```java
@RegisterRestClient(configKey = "evolution-api")
@Path("/message")
public interface EvolutionApiClient {

    @POST
    @Path("/sendText/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response sendText(
        @PathParam("instance") String instance,
        @HeaderParam("apikey") String apiKey,
        SendTextRequest body
    );
}
```

`SendTextRequest` record: `{ String number, String text }`.

Add to `application.properties`:
```properties
quarkus.rest-client.evolution-api.url=${EVOLUTION_API_URL:http://localhost:8080}
```

#### 2. NotificationService
**File**: `src/main/java/turtle/notification/NotificationService.java`

```java
@ApplicationScoped
public class NotificationService {

    @Inject EvolutionApiClient client;
    @ConfigProperty(name = "evolution.api.key")   String apiKey;
    @ConfigProperty(name = "evolution.api.instance") String instance;

    public void send(String phone, String message) {
        try {
            client.sendText(instance, apiKey, new SendTextRequest(phone, message));
        } catch (Exception e) {
            // log and swallow — notification failure must not break the booking flow
            Log.warnf("WhatsApp notification failed for %s: %s", phone, e.getMessage());
        }
    }
}
```

#### 3. BookingEventObserver
**File**: `src/main/java/turtle/notification/BookingEventObserver.java`

```java
@ApplicationScoped
public class BookingEventObserver {

    @Inject NotificationService notifications;

    void onCreated(@Observes BookingCreatedEvent e) {
        Booking b = e.booking();
        notifications.send(
            b.coach.phone,
            "New booking request from " + b.client.name + " for " + b.availability.startsAt
        );
    }

    void onApproved(@Observes BookingApprovedEvent e) {
        Booking b = e.booking();
        notifications.send(
            b.client.phone,
            "Your session with " + b.coach.name + " on " + b.availability.startsAt + " has been APPROVED."
        );
    }

    void onRejected(@Observes BookingRejectedEvent e) {
        Booking b = e.booking();
        notifications.send(
            b.client.phone,
            "Your booking request on " + b.availability.startsAt + " was not accepted. Please choose another slot."
        );
    }
}
```

### Success Criteria

#### Automated Verification
- [ ] `./mvnw test -Dtest=NotificationServiceTest` — mock `EvolutionApiClient`; assert `send()` is called with correct phone and message text on each event.
- [ ] `./mvnw compile` with no warnings about missing `@RestClient`.

#### Manual Verification
- [ ] With a real Evolution API instance running (or ngrok tunnel), booking a session delivers a WhatsApp message to the coach's phone.
- [ ] Notification failure (Evolution API down) does not return 500 to the client — booking still succeeds.

**Pause here for manual confirmation before proceeding to Phase 7.**

---

## Phase 7: Chat Module

### Overview
Per-booking message thread, accessible only after the booking is APPROVED.

### Changes Required

#### 1. ChatResource
**File**: `src/main/java/turtle/chat/ChatResource.java`

```
GET  /bookings/{id}/messages   @Authenticated — list messages (newest-last), assert booking APPROVED and caller is participant
POST /bookings/{id}/messages   @Authenticated — body: {content}, assert same guards, persist, fire ChatMessageEvent
```

#### 2. ChatService
- `listMessages(Long bookingId, Long callerId)` — assert booking status `APPROVED` and `callerId` is client or coach of that booking; return messages ordered by `sentAt ASC`.
- `sendMessage(Long bookingId, Long senderId, String content)` — same guards, persist `ChatMessage`, fire `ChatMessageSentEvent`.

#### 3. ChatMessageSentEvent + Observer
**Observer** in `NotificationService` (or `BookingEventObserver`):
- Find the other participant (if sender is client → notify coach, and vice versa).
- `notifications.send(recipient.phone, sender.name + ": " + content)`.

### Success Criteria

#### Automated Verification
- [ ] `./mvnw test -Dtest=ChatResourceTest` passes.
- [ ] `POST /bookings/{id}/messages` on a PENDING booking returns 403.
- [ ] Non-participant user cannot read messages (returns 403).

#### Manual Verification
- [ ] After approval, client sends a message; coach receives a WhatsApp notification.
- [ ] Messages persist across sessions (survive app restart).

---

## Testing Strategy

### Unit Tests (per phase)
- `AuthResourceTest` — register, login, duplicate email, bad password.
- `CoachResourceTest` — add slot, list free slots, overlap rejection, role guard.
- `BookingResourceTest` — full lifecycle, double-booking guard, ownership guards.
- `NotificationServiceTest` — mock HTTP client, verify message content per event type.
- `ChatResourceTest` — guard on unapproved booking, participant-only access.

Use `@QuarkusTest` + `@InjectMock` (Mockito via `quarkus-junit5-mockito`) for unit tests; use `@QuarkusTestResource` with Testcontainers PostgreSQL (`quarkus-jdbc-postgresql` ships a dev-service) for integration tests.

### Manual Testing Steps
1. Start `./mvnw quarkus:dev` (Dev Services starts a Postgres container automatically).
2. Register a COACH and a CLIENT via `POST /auth/register`.
3. Coach adds an availability slot via `POST /coaches/{id}/availability`.
4. Client books it via `POST /bookings`.
5. Confirm WhatsApp notification arrives on coach's phone.
6. Coach approves via `PATCH /bookings/{id}/approve`.
7. Confirm WhatsApp notification arrives on client's phone.
8. Client and coach exchange messages via `POST /bookings/{id}/messages`.
9. Confirm WhatsApp notification on each message.

---

## Migration Notes

The V1 Flyway migration handles the initial schema. Future schema changes require new versioned files (`V2__...sql`, `V3__...sql`). Never edit `V1__initial_schema.sql` once it has been applied.

---

## References

- Evolution API docs: https://doc.evolution-api.com/v2/en/
- Evolution API GitHub: https://github.com/EvolutionAPI/evolution-api
- Quarkus JWT guide: https://quarkus.io/guides/security-jwt
- Quarkus REST client: https://quarkus.io/guides/rest-client-reactive
- Quarkus Flyway: https://quarkus.io/guides/flyway
- Panache active record pattern: https://quarkus.io/guides/hibernate-orm-panache
