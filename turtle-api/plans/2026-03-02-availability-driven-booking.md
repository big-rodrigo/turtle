# Availability-Driven Booking Implementation Plan

## Overview

Restructure the booking system so that `TimeWindow` creation materializes concrete `Availability`
slots. Clients always book by referencing `availabilityId`(s), removing the dual-path complexity
that currently exists (`availabilityId` vs `timeWindowId + date + startTime`). Multiple sequential
`Availability` slots can be bundled into a single `Booking` (one coaching session).

---

## Current State Analysis

### The Problem

Two competing booking paths exist throughout the codebase:

- **Path A** (`availabilityId`): Client references a manually-created `Availability` row.
- **Path B** (`timeWindowId + date + startTime`): Client specifies a time window and computes a
  virtual slot on the fly; nothing is persisted until booking.

This dual-path forces every layer (`Booking` entity, `BookingService`, `BookingResource`,
`BookingResponse`) to branch on which path was used. Querying "free/booked/expired slots for a
time window" requires re-deriving virtual slots at query time (`TimeWindowService.deriveSlots`).

### Key Discoveries

- `Booking.java:32-38` — `@OneToOne Availability` + `@ManyToOne TimeWindow` + `startsAt`/`endsAt`
  columns; times come from different fields depending on source.
- `BookingResource.java:122-124` — `toResponse()` branches on `b.timeWindow != null` to pick time
  source.
- `Booking.java:55-73` — `findActiveByCoachOnDate` runs two separate queries and merges results.
- `TimeWindowService.java:55-87` — `deriveSlots()` generates virtual slots, filters booked ones
  by cross-referencing `Booking` table each time.
- `BookingService.java:61-101` — `createFromTimeWindow()` is 40 lines of validation and overlap
  detection that would be unnecessary if slots were already materialized.
- `CreateBookingRequest.java` — four nullable fields for two incompatible paths.
- `Availability.java:31-34` — `findFreeByCoach` only works for manually-created slots; does not
  include time-window slots.
- V4 migration added `booking_source_check` constraint enforcing exactly one path per row.

### What Exists Today (summary)

| Concept | Now |
|---|---|
| Manual coach-created slots | `POST /coaches/{id}/availability` → persists `Availability` |
| Time-window slots | Virtual; derived on every query; not persisted |
| Booking a slot | Either `availabilityId` OR `timeWindowId + date + startTime` |
| Multi-slot session | Not supported |

---

## Desired End State

| Concept | After |
|---|---|
| Coach publishes schedule | `POST /coaches/{id}/time-windows` → persists `TimeWindow` + all `Availability` rows for every slot in every day of the range |
| Browsing slots | `GET /coaches/{id}/slots?date=` returns actual `Availability` rows (with real IDs, status) |
| Booking a session | `POST /bookings` → `{ availabilityIds: [1,2,3], notes: "..." }` |
| Multi-slot session | Supported — multiple consecutive `Availability` IDs in one `Booking` |
| Querying time window state | `SELECT * FROM availability WHERE time_window_id = X` trivially yields all, booked, expired, free |

### Verification

- `POST /coaches/{id}/time-windows` creates N availability rows (N = days × slots/day).
- `GET /coaches/{id}/slots?date=YYYY-MM-DD` returns those rows with `status` field.
- `POST /bookings` with `[id1, id2]` where both slots are consecutive, unbooked, same coach → creates one `Booking` linking both.
- Booking the same slot twice → 409.
- Non-consecutive slots → 400.
- Cancel booking → all linked availability slots are freed.

---

## What We Are NOT Doing

- Keeping manual availability creation (`POST /coaches/{id}/availability` will be removed).
- Keeping the `timeWindowId + date + startTime` booking path.
- Supporting non-consecutive multi-slot booking (slots must be adjacent for a session).
- Adding a separate "publish" action — materialization happens on `TimeWindow` creation.
- Lazy/deferred slot generation (all slots for the window range are created immediately).

---

## Implementation Approach

1. **Materialize slots on TimeWindow creation** — `TimeWindowService.create()` generates and persists
   all `Availability` rows.
2. **Link `Availability` back to its `TimeWindow`** — adds `time_window_id` FK.
3. **`Availability` tracks its booking** via nullable `booking_id` FK (replaces the OneToOne on `Booking`).
4. **`Booking` owns `List<Availability>`** — one booking = one session = one or more consecutive slots.
5. **Single booking path** — `POST /bookings` always takes `availabilityIds`.
6. **Remove dead code** — manual slot endpoints, `createFromTimeWindow`, dual-path guards.

---

## Phase 1: Database Schema Migration

### Overview

Add `time_window_id` and `booking_id` to `availability`. Migrate existing data. Drop old columns
and constraints from `booking`.

### Changes Required

#### 1. Migration `V5__availability_from_time_window.sql`

```sql
-- Link availability slots back to the time window that generated them
ALTER TABLE availability
    ADD COLUMN time_window_id BIGINT REFERENCES time_window(id);

-- A booking now owns many availabilities (nullable until booking is created)
ALTER TABLE availability
    ADD COLUMN booking_id BIGINT REFERENCES booking(id);

-- Migrate existing bookings: mark their availability as owned by that booking
UPDATE availability a
SET booking_id = b.id
FROM booking b
WHERE b.availability_id = a.id;

-- Drop the old one-to-one FK and the now-obsolete time-window columns from booking
ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_source_check;
ALTER TABLE booking DROP COLUMN availability_id;
ALTER TABLE booking DROP COLUMN time_window_id;
ALTER TABLE booking DROP COLUMN starts_at;
ALTER TABLE booking DROP COLUMN ends_at;

-- Index for querying all slots in a time window by status
CREATE INDEX idx_availability_time_window ON availability (time_window_id, booking_id, starts_at);
```

### Success Criteria

#### Automated Verification
- [ ] Migration applies cleanly: `./mvnw flyway:migrate` (or `./mvnw quarkus:dev` auto-applies)
- [ ] All existing tests still compile: `./mvnw test-compile`

---

## Phase 2: Update Entities

### Overview

Reflect the new schema in `Availability` and `Booking` JPA entities, and add an `AvailabilityStatus`
enum derived at query time.

### Changes Required

#### 1. `Availability.java`

**File**: `src/main/java/turtle/coach/Availability.java`

Add `timeWindow` and `booking` relationships. Add derived `status()` helper. Update finders.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "time_window_id")
public TimeWindow timeWindow;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "booking_id")
public Booking booking;

// Derived — not stored
public AvailabilityStatus status() {
    if (booking != null)       return AvailabilityStatus.BOOKED;
    if (startsAt.isBefore(LocalDateTime.now())) return AvailabilityStatus.EXPIRED;
    return AvailabilityStatus.AVAILABLE;
}

// Replace old findFreeByCoach — now used only for time-window-sourced slots
public static List<Availability> findByTimeWindow(Long timeWindowId) {
    return list("timeWindow.id", timeWindowId);
}

public static List<Availability> findFreeByTimeWindow(Long timeWindowId) {
    return list("timeWindow.id = ?1 AND booking IS NULL AND startsAt > ?2",
            timeWindowId, LocalDateTime.now());
}

public static List<Availability> findFreeByCoachOnDate(Long coachId, LocalDate date) {
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.plusDays(1).atStartOfDay();
    return list("coach.id = ?1 AND booking IS NULL AND startsAt >= ?2 AND startsAt < ?3 AND startsAt > ?4",
            coachId, from, to, LocalDateTime.now());
}
```

#### 2. New `AvailabilityStatus.java`

**File**: `src/main/java/turtle/coach/AvailabilityStatus.java`

```java
package turtle.coach;

public enum AvailabilityStatus {
    AVAILABLE, BOOKED, EXPIRED
}
```

#### 3. `Booking.java`

**File**: `src/main/java/turtle/booking/Booking.java`

Replace `@OneToOne Availability` + `TimeWindow` + `startsAt`/`endsAt` fields with:

```java
@OneToMany(mappedBy = "booking", fetch = FetchType.EAGER)
@OrderBy("startsAt ASC")
public List<Availability> slots = new ArrayList<>();

// Convenience helpers replacing the old dual-path branches
public LocalDateTime startsAt() {
    return slots.isEmpty() ? null : slots.get(0).startsAt;
}

public LocalDateTime endsAt() {
    return slots.isEmpty() ? null : slots.get(slots.size() - 1).endsAt;
}
```

Simplify `findActiveByCoachOnDate`:

```java
public static List<Booking> findActiveByCoachOnDate(Long coachId, LocalDate date) {
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.plusDays(1).atStartOfDay();
    List<BookingStatus> active = List.of(BookingStatus.PENDING, BookingStatus.APPROVED);
    // Join through slots
    return getEntityManager().createQuery(
        "SELECT DISTINCT b FROM Booking b JOIN b.slots s " +
        "WHERE b.coach.id = :coach AND s.startsAt >= :from AND s.startsAt < :to AND b.status IN :statuses",
        Booking.class)
        .setParameter("coach", coachId)
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("statuses", active)
        .getResultList();
}
```

### Success Criteria

#### Automated Verification
- [ ] Project compiles: `./mvnw compile`

---

## Phase 3: Materialize Slots in TimeWindowService

### Overview

When a `TimeWindow` is created, generate and persist one `Availability` row per slot per day
in the window's date range.

### Changes Required

#### 1. `TimeWindowService.java`

**File**: `src/main/java/turtle/coach/TimeWindowService.java`

Add slot generation at the end of `create()`:

```java
@Transactional
public TimeWindow create(Long coachId, TimeWindowRequest req) {
    // ... existing validation ...
    tw.persist();

    // Materialize all slots
    AppUser coach = AppUser.findById(coachId);
    LocalDate cursor = req.startDate();
    while (!cursor.isAfter(req.endDate())) {
        LocalTime slotStart = req.dailyStartTime();
        while (!slotStart.plusMinutes(req.unitOfWorkMinutes()).isAfter(req.dailyEndTime())) {
            Availability slot = new Availability();
            slot.coach = coach;
            slot.timeWindow = tw;
            slot.startsAt = LocalDateTime.of(cursor, slotStart);
            slot.endsAt = slot.startsAt.plusMinutes(req.unitOfWorkMinutes());
            slot.persist();
            slotStart = slotStart.plusMinutes(req.unitOfWorkMinutes());
        }
        cursor = cursor.plusDays(1);
    }
    return tw;
}
```

Update `deriveSlots()` to read from `Availability` table instead of computing virtually.
Rename to `getSlotsForDate()` returning `AvailabilityResponse` (with real `availabilityId`):

```java
public List<AvailabilityResponse> getSlotsForDate(Long coachId, LocalDate date) {
    return Availability.findByCoachOnDate(coachId, date).stream()
        .map(a -> new AvailabilityResponse(a.id, a.startsAt, a.endsAt, a.status()))
        .toList();
}
```

Update `delete()` to reject if any slots are booked:

```java
@Transactional
public void delete(Long windowId, Long coachId) {
    TimeWindow tw = TimeWindow.findById(windowId);
    if (tw == null) throw new WebApplicationException("Time window not found", 404);
    if (!tw.coach.id.equals(coachId)) throw new WebApplicationException("Forbidden", 403);
    boolean hasBookings = Availability.<Availability>list("timeWindow.id = ?1 AND booking IS NOT NULL", windowId)
        .stream().anyMatch(a -> a.booking != null);
    if (hasBookings) throw new WebApplicationException("Cannot delete a time window with active bookings", 409);
    Availability.delete("timeWindow.id", windowId);
    tw.delete();
}
```

### Success Criteria

#### Automated Verification
- [ ] Project compiles: `./mvnw compile`

#### Manual Verification
- [ ] `POST /coaches/{id}/time-windows` with a 3-day window of 2 slots/day → 6 `availability` rows in DB.
- [ ] `GET /coaches/{id}/slots?date=` returns those rows with correct `availabilityId` values.

---

## Phase 4: Refactor Booking to Single-Path (Multi-Slot)

### Overview

`BookingService.create()` now takes `List<Long> availabilityIds`. All slots must be: same coach,
consecutive (no gaps between them), unbooked, in the future.

### Changes Required

#### 1. `CreateBookingRequest.java`

**File**: `src/main/java/turtle/booking/dto/CreateBookingRequest.java`

```java
public record CreateBookingRequest(
    @NotNull @Size(min = 1) List<Long> availabilityIds,
    String notes
) {}
```

#### 2. `BookingResponse.java`

**File**: `src/main/java/turtle/booking/dto/BookingResponse.java`

Add `availabilityIds` list; `startsAt`/`endsAt` derived from first/last slot:

```java
public record BookingResponse(
    Long id,
    Long clientId,
    String clientName,
    Long coachId,
    String coachName,
    List<Long> availabilityIds,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    BookingStatus status,
    String notes,
    LocalDateTime createdAt
) {}
```

#### 3. `BookingService.java`

**File**: `src/main/java/turtle/booking/BookingService.java`

Replace `create(Long, Long, String)` and `createFromTimeWindow(...)` with a single:

```java
@Transactional
public Booking create(Long clientId, List<Long> availabilityIds, String notes) {
    if (availabilityIds == null || availabilityIds.isEmpty())
        throw new WebApplicationException("At least one availabilityId is required", 400);

    List<Availability> slots = availabilityIds.stream()
        .map(id -> {
            Availability a = Availability.findById(id);
            if (a == null) throw new WebApplicationException("Availability " + id + " not found", 404);
            return a;
        })
        .sorted(Comparator.comparing(a -> a.startsAt))
        .toList();

    // All slots must belong to the same coach
    Long coachId = slots.get(0).coach.id;
    if (slots.stream().anyMatch(a -> !a.coach.id.equals(coachId)))
        throw new WebApplicationException("All slots must belong to the same coach", 400);

    // All slots must be unbooked
    if (slots.stream().anyMatch(a -> a.booking != null))
        throw new WebApplicationException("One or more slots are already booked", 409);

    // All slots must be in the future
    if (slots.stream().anyMatch(a -> a.startsAt.isBefore(LocalDateTime.now())))
        throw new WebApplicationException("One or more slots are in the past", 400);

    // Slots must be consecutive (each slot's endsAt == next slot's startsAt)
    for (int i = 0; i < slots.size() - 1; i++) {
        if (!slots.get(i).endsAt.equals(slots.get(i + 1).startsAt))
            throw new WebApplicationException("Slots must be consecutive with no gaps", 400);
    }

    AppUser client = AppUser.findById(clientId);
    AppUser coach = AppUser.findById(coachId);

    Booking booking = new Booking();
    booking.client = client;
    booking.coach = coach;
    booking.status = BookingStatus.PENDING;
    booking.notes = notes;
    booking.createdAt = LocalDateTime.now();
    booking.persist();

    // Link availability slots to this booking
    for (Availability slot : slots) {
        slot.booking = booking;
    }
    booking.slots = new ArrayList<>(slots);

    bookingCreatedEvent.fire(new BookingCreatedEvent(booking));
    return booking;
}
```

Update `reject()` and `cancel()` to free slots by nulling `slot.booking`:

```java
// In reject():
booking.slots.forEach(s -> s.booking = null);

// In cancel():
booking.slots.forEach(s -> s.booking = null);
```

Remove `createFromTimeWindow()` entirely.

#### 4. `BookingResource.java`

**File**: `src/main/java/turtle/booking/BookingResource.java`

Simplify `create()` — remove the if/else branching:

```java
@POST
@RolesAllowed("CLIENT")
public Response create(@Valid CreateBookingRequest req) {
    Long clientId = Long.parseLong(jwt.getSubject());
    Booking booking = bookingService.create(clientId, req.availabilityIds(), req.notes());
    return Response.status(201).entity(toResponse(booking)).build();
}
```

Simplify `toResponse()`:

```java
private BookingResponse toResponse(Booking b) {
    List<Long> ids = b.slots.stream().map(s -> s.id).toList();
    return new BookingResponse(
        b.id, b.client.id, b.client.name,
        b.coach.id, b.coach.name,
        ids, b.startsAt(), b.endsAt(),
        b.status, b.notes, b.createdAt);
}
```

### Success Criteria

#### Automated Verification
- [ ] All tests pass: `./mvnw test`

#### Manual Verification
- [ ] `POST /bookings` with `{"availabilityIds": [1], "notes": "..."}` → 201 with PENDING status.
- [ ] `POST /bookings` with `{"availabilityIds": [1, 2]}` where 2 immediately follows 1 → 201.
- [ ] `POST /bookings` with the same slot ID again → 409.
- [ ] `POST /bookings` with non-consecutive slot IDs → 400.
- [ ] `DELETE /bookings/{id}` (cancel) → the `availability.booking_id` is NULL again in DB.

---

## Phase 5: Update CoachResource and Slots Endpoint

### Overview

Update `GET /coaches/{id}/slots` to return real `Availability` records (with IDs). Remove legacy
manual-availability endpoints. Update `AvailabilityResponse` DTO to carry status.

### Changes Required

#### 1. `AvailabilityResponse.java`

**File**: `src/main/java/turtle/coach/dto/AvailabilityResponse.java`

Add `status` field:

```java
public record AvailabilityResponse(
    Long id,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    AvailabilityStatus status
) {}
```

#### 2. `CoachResource.java`

**File**: `src/main/java/turtle/coach/CoachResource.java`

- Remove `getAvailability()` (`GET /coaches/{id}/availability`)
- Remove `addSlot()` (`POST /coaches/{id}/availability`)
- Remove `deleteSlot()` (`DELETE /coaches/availability/{slotId}`)
- Update `getAvailableSlots()` to call the new `getSlotsForDate()`:

```java
@GET
@Path("/{id}/slots")
public List<AvailabilityResponse> getAvailableSlots(
        @PathParam("id") Long coachId,
        @QueryParam("date") LocalDate date) {
    if (date == null) throw new WebApplicationException("Query parameter 'date' is required", 400);
    return timeWindowService.getSlotsForDate(coachId, date);
}
```

#### 3. `CoachService.java`

**File**: `src/main/java/turtle/coach/CoachService.java`

Remove `addSlot()`, `deleteSlot()`, `listFreeSlots()`. Keep only `listCoaches()`.

#### 4. Remove dead DTOs

- Delete `src/main/java/turtle/coach/dto/AvailabilityRequest.java`
- Delete `src/main/java/turtle/coach/dto/SlotResponse.java`

### Success Criteria

#### Automated Verification
- [ ] Project compiles with no references to removed classes: `./mvnw compile`
- [ ] All tests pass: `./mvnw test`

#### Manual Verification
- [ ] `GET /coaches/{id}/slots?date=2026-03-10` returns slots with `id`, `startsAt`, `endsAt`, `status` fields.
- [ ] After booking a slot, re-querying the same date shows that slot with `status: BOOKED`.
- [ ] Past slots show `status: EXPIRED`.
- [ ] Swagger UI (`/swagger-ui`) no longer shows the removed availability endpoints.

---

## Testing Strategy

### Unit Tests to Add / Update

- `BookingServiceTest` — test single-slot booking, multi-slot consecutive booking,
  non-consecutive rejection (400), already-booked rejection (409), cancel frees slots.
- `TimeWindowServiceTest` — verify correct number of `Availability` rows created for a given range;
  verify delete blocks when slots are booked.

### Integration / Manual Testing Steps

1. Register a coach, approve them.
2. `POST /coaches/{id}/time-windows` with a 2-day range, `unitOfWorkMinutes=60`, 2 slots/day.
3. Verify 4 rows in `availability` table, all with `time_window_id` set.
4. `GET /coaches/{id}/slots?date=<day1>` → 2 slots with `status: AVAILABLE`.
5. `POST /bookings` with `availabilityIds: [id1, id2]` (consecutive on same day) → 201.
6. Re-query slots → both slots now `status: BOOKED`.
7. `DELETE /bookings/{id}` → slots back to `AVAILABLE`.
8. `POST /bookings` with `availabilityIds: [id1, id3]` (non-consecutive, day gap) → 400.
9. `DELETE /coaches/time-windows/{id}` while a booking exists → 409.

---

## Migration Notes

The V5 migration performs a data migration for any existing `booking → availability` links before
dropping the old columns. In a production rollout, apply V5 during a maintenance window with no
active write traffic. The `booking_source_check` constraint is dropped as part of V5.

If you want to wipe local dev data instead, truncate and re-create:
```sql
TRUNCATE booking, availability, time_window CASCADE;
```
Then re-create time windows and the slots will be generated fresh.

---

## References

- Current booking dual-path: `BookingResource.java:45-57`, `BookingService.java:35-101`
- Current slot derivation: `TimeWindowService.java:55-87`
- Current dual-path entity: `Booking.java:30-73`
- Existing migration history: `src/main/resources/db/migration/V1__initial_schema.sql` through `V4__booking_time_window.sql`
