# Time Windows Implementation Plan

## Overview

Add a `TimeWindow` concept that lets coaches define recurring availability schedules (date range + daily time range + unit of work duration + price + priority). Clients book sessions against these windows by picking a specific slot. Overlapping windows resolve by priority.

---

## Current State Analysis

- **Availability** (`src/main/java/turtle/coach/Availability.java`): individual, manually-created bookable slots with `startsAt`/`endsAt` datetimes and a `booked` flag.
- **Booking** (`src/main/java/turtle/booking/Booking.java`): references a single `Availability` (one-to-one, unique constraint).
- **CoachService** (`src/main/java/turtle/coach/CoachService.java`): coaches add/delete slots and list free slots; slot overlap is already validated (lines 22–33).
- Flyway manages the schema (`V1`, `V2`); Hibernate validates only.

### Key Constraints:
- Hibernate `validate` mode — all schema changes must come via Flyway migrations.
- Entities use Panache Active Record pattern.
- Services fire CDI events for notifications; that pattern must be preserved.
- JWT role groups: `CLIENT`, `COACH`, `COACH_PENDING`, `ADMIN` — new endpoints must respect them.

---

## Desired End State

Coaches can define **time windows** (structured availability templates). Clients query the derived available slots for a coach and book a specific one. The feature co-exists with the existing manual-slot system.

### Verification:
- `POST /coaches/{id}/time-windows` creates a window (COACH only, own profile).
- `GET /coaches/{id}/time-windows` lists all windows for a coach (public).
- `DELETE /coaches/time-windows/{windowId}` removes a window (COACH, own, no active bookings).
- `GET /coaches/{id}/slots?date=YYYY-MM-DD` returns derived available time slots for a date, respecting priority and existing bookings.
- `POST /bookings` accepts `timeWindowId + date + startTime` (in addition to existing `availabilityId`).
- Overlapping windows: the window with the highest `priority` value wins.

---

## What We Are NOT Doing

- Replacing the existing manual `Availability` / `Booking` flow — it remains fully intact.
- Auto-generating `Availability` rows from `TimeWindow` — slots are derived on-the-fly.
- Recurring rules (weekly patterns, exclusion dates) — out of scope.
- Payment processing — price is informational only for now.
- Notifications for time-window booking creation/approval — will reuse the existing `BookingCreatedEvent`.

---

## Implementation Approach

Three phases, each independently deployable and testable:

1. **TimeWindow CRUD** — entity, migration, service, resource, DTOs.
2. **Slot derivation endpoint** — derive bookable slots from windows for a given date.
3. **Booking against a TimeWindow** — extend `Booking` + `BookingService` + `CreateBookingRequest`.

---

## Phase 1: TimeWindow Entity & CRUD

### Overview
Introduce `TimeWindow` as a first-class entity. Coaches can create, list, and delete their time windows.

### Changes Required

#### 1. Flyway Migration
**File**: `src/main/resources/db/migration/V3__time_windows.sql`

```sql
CREATE TABLE time_window (
    id                  BIGSERIAL PRIMARY KEY,
    coach_id            BIGINT       NOT NULL REFERENCES app_user(id),
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    daily_start_time    TIME         NOT NULL,
    daily_end_time      TIME         NOT NULL,
    unit_of_work_minutes INTEGER     NOT NULL CHECK (unit_of_work_minutes > 0),
    price_per_unit      NUMERIC(10,2),
    priority            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT tw_dates_check CHECK (end_date >= start_date),
    CONSTRAINT tw_times_check CHECK (daily_end_time > daily_start_time)
);
```

#### 2. Entity
**File**: `src/main/java/turtle/coach/TimeWindow.java`

```java
@Entity
@Table(name = "time_window")
public class TimeWindow extends PanacheEntityBase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    public AppUser coach;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(name = "daily_start_time", nullable = false)
    public LocalTime dailyStartTime;

    @Column(name = "daily_end_time", nullable = false)
    public LocalTime dailyEndTime;

    @Column(name = "unit_of_work_minutes", nullable = false)
    public int unitOfWorkMinutes;

    @Column(name = "price_per_unit")
    public BigDecimal pricePerUnit;

    @Column(name = "priority", nullable = false)
    public int priority = 0;

    public static List<TimeWindow> findByCoach(Long coachId) {
        return list("coach.id", coachId);
    }

    public static List<TimeWindow> findByCoachForDate(Long coachId, LocalDate date) {
        return list("coach.id = ?1 AND startDate <= ?2 AND endDate >= ?2", coachId, date);
    }
}
```

#### 3. DTOs
**File**: `src/main/java/turtle/coach/dto/TimeWindowRequest.java`

```java
public record TimeWindowRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull LocalTime dailyStartTime,
    @NotNull LocalTime dailyEndTime,
    @Positive int unitOfWorkMinutes,
    BigDecimal pricePerUnit,
    int priority
) {}
```

**File**: `src/main/java/turtle/coach/dto/TimeWindowResponse.java`

```java
public record TimeWindowResponse(
    Long id,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime dailyStartTime,
    LocalTime dailyEndTime,
    int unitOfWorkMinutes,
    BigDecimal pricePerUnit,
    int priority
) {}
```

#### 4. Service
**File**: `src/main/java/turtle/coach/TimeWindowService.java`

```java
@ApplicationScoped
public class TimeWindowService {

    @Transactional
    public TimeWindow create(Long coachId, TimeWindowRequest req) {
        if (!req.endDate().isAfter(req.startDate()) && !req.endDate().equals(req.startDate())) {
            throw new WebApplicationException("endDate must be >= startDate", 400);
        }
        if (!req.dailyEndTime().isAfter(req.dailyStartTime())) {
            throw new WebApplicationException("dailyEndTime must be after dailyStartTime", 400);
        }
        long windowMinutes = Duration.between(req.dailyStartTime(), req.dailyEndTime()).toMinutes();
        if (req.unitOfWorkMinutes() > windowMinutes) {
            throw new WebApplicationException("unitOfWorkMinutes exceeds the daily window duration", 400);
        }
        AppUser coach = AppUser.findById(coachId);
        TimeWindow tw = new TimeWindow();
        tw.coach = coach;
        tw.startDate = req.startDate();
        tw.endDate = req.endDate();
        tw.dailyStartTime = req.dailyStartTime();
        tw.dailyEndTime = req.dailyEndTime();
        tw.unitOfWorkMinutes = req.unitOfWorkMinutes();
        tw.pricePerUnit = req.pricePerUnit();
        tw.priority = req.priority();
        tw.persist();
        return tw;
    }

    public List<TimeWindow> listForCoach(Long coachId) {
        return TimeWindow.findByCoach(coachId);
    }

    @Transactional
    public void delete(Long windowId, Long coachId) {
        TimeWindow tw = TimeWindow.findById(windowId);
        if (tw == null) throw new WebApplicationException("Time window not found", 404);
        if (!tw.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
        // Phase 3 will add a check here: cannot delete if active bookings exist against this window
        tw.delete();
    }
}
```

#### 5. Resource
**File**: `src/main/java/turtle/coach/CoachResource.java` — add methods:

```java
// GET /coaches/{id}/time-windows  (public)
@GET
@Path("/{id}/time-windows")
public List<TimeWindowResponse> listTimeWindows(@PathParam("id") Long coachId) {
    return timeWindowService.listForCoach(coachId)
        .stream().map(this::toResponse).toList();
}

// POST /coaches/{id}/time-windows  (COACH only, own profile)
@POST
@Path("/{id}/time-windows")
@RolesAllowed("COACH")
public Response addTimeWindow(@PathParam("id") Long coachId,
                               @Valid TimeWindowRequest req,
                               @Context SecurityContext ctx) {
    Long callerId = Long.parseLong(ctx.getUserPrincipal().getName());
    if (!callerId.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
    TimeWindow tw = timeWindowService.create(coachId, req);
    return Response.status(201).entity(toResponse(tw)).build();
}

// DELETE /coaches/time-windows/{windowId}  (COACH only)
@DELETE
@Path("/time-windows/{windowId}")
@RolesAllowed("COACH")
public Response deleteTimeWindow(@PathParam("windowId") Long windowId,
                                  @Context SecurityContext ctx) {
    Long callerId = Long.parseLong(ctx.getUserPrincipal().getName());
    timeWindowService.delete(windowId, callerId);
    return Response.noContent().build();
}
```

### Success Criteria

#### Automated Verification:
- [x] Migration applies: `docker compose up -d && ./mvnw quarkus:dev` (Flyway runs V3)
- [x] Tests pass: `./mvnw test` (pre-existing failures only, unrelated to this phase)
- [x] No compilation errors: `./mvnw compile`

#### Manual Verification:
- [x] Coach can create a time window via `POST /coaches/{id}/time-windows`
- [x] Time window appears in `GET /coaches/{id}/time-windows`
- [x] Validation errors returned for invalid dates/times (endDate < startDate, etc.)
- [x] Non-owner coach gets 403 on create
- [x] Coach can delete own time window via `DELETE /coaches/time-windows/{windowId}`
- [x] Swagger UI shows new endpoints under `/swagger-ui`

**Pause for manual confirmation before Phase 2.**

---

## Phase 2: Slot Derivation Endpoint

### Overview
Expose a `GET /coaches/{id}/slots?date=YYYY-MM-DD` endpoint that computes available bookable time slots for a coach on a given date, derived from matching time windows (respecting priority and existing bookings).

### Changes Required

#### 1. Slot Derivation Logic in TimeWindowService

```java
public List<SlotResponse> deriveSlots(Long coachId, LocalDate date) {
    List<TimeWindow> windows = TimeWindow.findByCoachForDate(coachId, date);
    if (windows.isEmpty()) return List.of();

    // Pick the window with the highest priority (ties: smallest id wins for determinism)
    TimeWindow best = windows.stream()
        .max(Comparator.comparingInt((TimeWindow tw) -> tw.priority)
                       .thenComparing(tw -> -tw.id))
        .orElseThrow();

    // Generate all slots within the daily window
    List<LocalTime> slotStarts = new ArrayList<>();
    LocalTime cursor = best.dailyStartTime;
    while (!cursor.plusMinutes(best.unitOfWorkMinutes).isAfter(best.dailyEndTime)) {
        slotStarts.add(cursor);
        cursor = cursor.plusMinutes(best.unitOfWorkMinutes);
    }

    // Filter out already-booked slots (approved/pending bookings for this coach on this date)
    Set<LocalDateTime> bookedStartTimes = bookedTimesForCoachOnDate(coachId, date);

    return slotStarts.stream()
        .filter(t -> !bookedStartTimes.contains(LocalDateTime.of(date, t)))
        .map(t -> new SlotResponse(
            best.id,
            date,
            t,
            t.plusMinutes(best.unitOfWorkMinutes),
            best.unitOfWorkMinutes,
            best.pricePerUnit
        ))
        .toList();
}

private Set<LocalDateTime> bookedTimesForCoachOnDate(Long coachId, LocalDate date) {
    // Query bookings (PENDING or APPROVED) where coach.id=coachId and booking date = date
    // Requires a new query on the Booking entity (see below)
    return Booking.findActiveByCoachOnDate(coachId, date)
        .stream()
        .map(b -> b.startsAt)
        .collect(Collectors.toSet());
}
```

#### 2. Extend Booking Entity
**File**: `src/main/java/turtle/booking/Booking.java` — add query:

```java
// Returns PENDING + APPROVED bookings for a coach on a given date
public static List<Booking> findActiveByCoachOnDate(Long coachId, LocalDate date) {
    return list(
        "coach.id = ?1 AND startsAt >= ?2 AND startsAt < ?3 AND status IN ('PENDING','APPROVED')",
        coachId,
        date.atStartOfDay(),
        date.plusDays(1).atStartOfDay()
    );
}
```

**Note**: `Booking.startsAt` is currently derived from `Booking.availability.startsAt`. Phase 3 will add `startsAt` directly to `Booking`; for Phase 2, the query must join through availability:

```java
public static List<Booking> findActiveByCoachOnDate(Long coachId, LocalDate date) {
    return list(
        "coach.id = ?1 AND availability.startsAt >= ?2 AND availability.startsAt < ?3 " +
        "AND status IN ?4",
        coachId,
        date.atStartOfDay(),
        date.plusDays(1).atStartOfDay(),
        List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
    );
}
```

#### 3. DTO
**File**: `src/main/java/turtle/coach/dto/SlotResponse.java`

```java
public record SlotResponse(
    Long timeWindowId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    int durationMinutes,
    BigDecimal pricePerUnit
) {}
```

#### 4. Resource Endpoint — add to CoachResource

```java
@GET
@Path("/{id}/slots")
public List<SlotResponse> getAvailableSlots(
        @PathParam("id") Long coachId,
        @QueryParam("date") @NotNull LocalDate date) {
    return timeWindowService.deriveSlots(coachId, date);
}
```

### Success Criteria

#### Automated Verification:
- [x] Tests pass: `./mvnw test` (same pre-existing failures only)
- [x] Slot endpoint compiles with no errors

#### Manual Verification:
- [x] `GET /coaches/{id}/slots?date=2026-03-02` returns slots matching window's UoW for that date
- [x] Slots already booked (via existing availability) do not appear
- [x] Date outside window range returns empty array
- [x] Highest-priority window is used when multiple overlap on the same date

**Pause for manual confirmation before Phase 3.**

---

## Phase 3: Booking Against a TimeWindow

### Overview
Extend `CreateBookingRequest` to accept `timeWindowId + date + startTime` as an alternative to `availabilityId`. The system creates a booking anchored to the time window slot.

### Changes Required

#### 1. Flyway Migration
**File**: `src/main/resources/db/migration/V4__booking_time_window.sql`

```sql
ALTER TABLE booking
    ADD COLUMN time_window_id BIGINT REFERENCES time_window(id),
    ADD COLUMN starts_at      TIMESTAMP,
    ADD COLUMN ends_at        TIMESTAMP;

-- Allow availability_id to be nullable (time-window bookings don't use it)
ALTER TABLE booking ALTER COLUMN availability_id DROP NOT NULL;

-- Constraint: exactly one of (availability_id, time_window_id) must be set
ALTER TABLE booking ADD CONSTRAINT booking_source_check
    CHECK (
        (availability_id IS NOT NULL AND time_window_id IS NULL) OR
        (availability_id IS NULL AND time_window_id IS NOT NULL)
    );
```

#### 2. Update Booking Entity
**File**: `src/main/java/turtle/booking/Booking.java`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "time_window_id")
public TimeWindow timeWindow;           // nullable

@Column(name = "starts_at")
public LocalDateTime startsAt;          // nullable for legacy availability-based bookings

@Column(name = "ends_at")
public LocalDateTime endsAt;            // nullable for legacy availability-based bookings
```

Also update `findActiveByCoachOnDate` from Phase 2 to handle both:

```java
public static List<Booking> findActiveByCoachOnDate(Long coachId, LocalDate date) {
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.plusDays(1).atStartOfDay();
    // Bookings from availability
    List<Booking> fromAvail = list(
        "coach.id = ?1 AND availability IS NOT NULL " +
        "AND availability.startsAt >= ?2 AND availability.startsAt < ?3 AND status IN ?4",
        coachId, from, to, List.of(BookingStatus.PENDING, BookingStatus.APPROVED));
    // Bookings from time windows
    List<Booking> fromWindows = list(
        "coach.id = ?1 AND timeWindow IS NOT NULL " +
        "AND startsAt >= ?2 AND startsAt < ?3 AND status IN ?4",
        coachId, from, to, List.of(BookingStatus.PENDING, BookingStatus.APPROVED));
    List<Booking> all = new ArrayList<>(fromAvail);
    all.addAll(fromWindows);
    return all;
}
```

#### 3. Update DTO
**File**: `src/main/java/turtle/booking/dto/CreateBookingRequest.java`

```java
public record CreateBookingRequest(
    // existing availability-based booking (keep nullable)
    Long availabilityId,

    // time-window-based booking
    Long timeWindowId,
    LocalDate date,
    LocalTime startTime,

    String notes
) {
    // validated in service — exactly one of (availabilityId, timeWindowId+date+startTime) must be set
}
```

#### 4. Update BookingService
**File**: `src/main/java/turtle/booking/BookingService.java`

Add overload or extend `create()`:

```java
@Transactional
public Booking createFromTimeWindow(Long clientId, Long timeWindowId,
                                     LocalDate date, LocalTime startTime, String notes) {
    TimeWindow tw = TimeWindow.findById(timeWindowId);
    if (tw == null) throw new WebApplicationException("Time window not found", 404);

    // Validate date is within window range
    if (date.isBefore(tw.startDate) || date.isAfter(tw.endDate))
        throw new WebApplicationException("Date is outside the time window range", 400);

    // Validate startTime fits a valid slot boundary
    long minutesFromStart = Duration.between(tw.dailyStartTime, startTime).toMinutes();
    if (minutesFromStart < 0 || minutesFromStart % tw.unitOfWorkMinutes != 0)
        throw new WebApplicationException("startTime does not align with a valid slot boundary", 400);

    LocalDateTime startsAt = LocalDateTime.of(date, startTime);
    LocalDateTime endsAt = startsAt.plusMinutes(tw.unitOfWorkMinutes);

    // Check endsAt does not exceed dailyEndTime
    if (endsAt.toLocalTime().isAfter(tw.dailyEndTime))
        throw new WebApplicationException("Slot exceeds the daily window end time", 400);

    // Check for conflicting bookings
    boolean conflict = !Booking.findActiveByCoachOnDate(tw.coach.id, date).stream()
        .filter(b -> {
            LocalDateTime bs = b.timeWindow != null ? b.startsAt : b.availability.startsAt;
            LocalDateTime be = b.timeWindow != null ? b.endsAt : b.availability.endsAt;
            return bs.isBefore(endsAt) && be.isAfter(startsAt);
        }).toList().isEmpty();
    if (conflict) throw new WebApplicationException("Slot is already booked", 409);

    AppUser client = AppUser.findById(clientId);
    Booking booking = new Booking();
    booking.client = client;
    booking.coach = tw.coach;
    booking.timeWindow = tw;
    booking.startsAt = startsAt;
    booking.endsAt = endsAt;
    booking.status = BookingStatus.PENDING;
    booking.notes = notes;
    booking.createdAt = LocalDateTime.now();
    booking.persist();

    bookingCreatedEvent.fire(new BookingCreatedEvent(booking));
    return booking;
}
```

Also update `BookingResource.create()` to dispatch between the two flows:

```java
@POST
@RolesAllowed("CLIENT")
public Response create(@Valid CreateBookingRequest req, @Context SecurityContext ctx) {
    Long clientId = Long.parseLong(ctx.getUserPrincipal().getName());
    Booking booking;
    if (req.availabilityId() != null) {
        booking = bookingService.create(clientId, req.availabilityId(), req.notes());
    } else if (req.timeWindowId() != null && req.date() != null && req.startTime() != null) {
        booking = bookingService.createFromTimeWindow(
            clientId, req.timeWindowId(), req.date(), req.startTime(), req.notes());
    } else {
        throw new WebApplicationException(
            "Provide either availabilityId or (timeWindowId + date + startTime)", 400);
    }
    return Response.status(201).entity(toResponse(booking)).build();
}
```

#### 5. Update BookingResponse
**File**: `src/main/java/turtle/booking/dto/BookingResponse.java`

Add `startsAt`, `endsAt` fields that are populated from either the `Availability` or directly from `Booking`:

```java
public record BookingResponse(
    Long id,
    Long clientId, String clientName,
    Long coachId, String coachName,
    LocalDateTime startsAt,  // unified field
    LocalDateTime endsAt,    // unified field
    BookingStatus status,
    String notes,
    LocalDateTime createdAt
) {}
```

Mapping in `BookingResource`:

```java
private BookingResponse toResponse(Booking b) {
    LocalDateTime startsAt = b.timeWindow != null
        ? b.startsAt
        : b.availability.startsAt;
    LocalDateTime endsAt = b.timeWindow != null
        ? b.endsAt
        : b.availability.endsAt;
    return new BookingResponse(b.id, b.client.id, b.client.name,
        b.coach.id, b.coach.name, startsAt, endsAt, b.status, b.notes, b.createdAt);
}
```

### Success Criteria

#### Automated Verification:
- [x] V4 migration applies cleanly
- [x] All existing tests still pass: `./mvnw test` (same pre-existing failures only)
- [x] Compilation succeeds: `./mvnw compile`

#### Manual Verification:
- [ ] Client books via `POST /bookings` with `{ "timeWindowId": 1, "date": "2026-03-05", "startTime": "15:00", "notes": "..." }` → 201
- [ ] Booking appears in `GET /bookings` with correct `startsAt`/`endsAt`
- [ ] Double-booking same slot returns 409
- [ ] Booking outside window date range returns 400
- [ ] Booking with misaligned startTime returns 400
- [ ] Existing `availabilityId`-based bookings still work
- [ ] Notifications (email/WhatsApp) fire correctly for time-window bookings

---

## Testing Strategy

### Unit Tests (per phase):
- `TimeWindowServiceTest`: date/time validation, slot boundary alignment, priority resolution
- `TimeWindowResourceTest`: role enforcement (COACH only for create/delete, public for list/slots), ownership check, 404 on invalid coach id

### Integration Tests:
- Full flow: create window → derive slots → book slot → confirm slot disappears from derived list
- Overlap scenario: two windows on same date, higher priority one's slots are returned
- Existing availability-based booking flow remains unaffected

### Manual Testing Steps:
1. Create two overlapping time windows for a coach, with different priorities
2. Call `GET /coaches/{id}/slots?date=...` — verify higher-priority window's slots appear
3. Book a slot → call slots endpoint again → verify booked slot is missing
4. Attempt to book same slot again → expect 409

---

## Performance Considerations

- `findByCoachForDate` query: add a composite index on `(coach_id, start_date, end_date)` in V3 migration.
- Slot derivation is in-memory; for large date ranges with many UoW units this is O(window_size / uow). Acceptable for coaching sessions (rarely >50 slots per day).
- `findActiveByCoachOnDate` runs two queries (one per booking source); acceptable until booking volume grows significantly.

---

## Migration Notes

- V3 is additive (new table only) — no data migration needed.
- V4 alters `booking` table: adds nullable columns and removes NOT NULL from `availability_id`. Existing rows are unaffected (they will have `availability_id` set and `time_window_id` NULL).
- The CHECK constraint in V4 ensures data integrity going forward.

---

## References

- Feature spec: `time-window.md`
- Existing availability: `src/main/java/turtle/coach/Availability.java`
- Existing booking: `src/main/java/turtle/booking/Booking.java`
- Coach service patterns: `src/main/java/turtle/coach/CoachService.java`
- Migration examples: `src/main/resources/db/migration/V1__initial_schema.sql`
