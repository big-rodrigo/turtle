# Frontend ↔ API Alignment Implementation Plan

## Overview

The Spring Boot API has evolved significantly since the frontend was last updated. Two major features
were added — **Time Windows** (coach recurring availability) and **multi-slot bookings** — requiring
a breaking change to the availability and booking endpoints. This plan brings the SvelteKit frontend
in sync with the current API contract.

---

## Current State Analysis

### Frontend state
- Uses **3 removed endpoints** that no longer exist in the API
- Uses **wrong request shape** for booking creation
- Missing UI for the Time Window management flow entirely

### Key mismatches discovered

| Area | Frontend expects | API provides |
|------|-----------------|--------------|
| List coach slots | `GET /coaches/{id}/availability` | **REMOVED** |
| Add individual slot | `POST /coaches/{id}/availability` with `{startsAt, endsAt}` | **REMOVED** |
| Delete individual slot | `DELETE /coaches/availability/{slotId}` | **REMOVED** |
| Slot shape | `{ id, startsAt, endsAt, booked: boolean }` | `{ id, startsAt, endsAt, status: 'AVAILABLE'\|'BOOKED'\|'EXPIRED' }` |
| Create booking | `POST /bookings` with `{ availabilityId: number }` (singular) | `{ availabilityIds: number[] }` (plural, array) |
| Booking response | no `availabilityIds`, no `endsAt` | includes `availabilityIds[]` and `endsAt` |
| Coach availability mgmt | Manages individual slots directly | Manages **Time Windows** (date range + daily times + slot duration) |
| Browse slots as client | Loads all slots for coach | Must query by date: `GET /coaches/{id}/slots?date=YYYY-MM-DD` |

### New API endpoints (not yet in frontend)

| Endpoint | Purpose |
|----------|---------|
| `GET /coaches/{id}/time-windows` | List coach's time window definitions |
| `POST /coaches/{id}/time-windows` | Create time window (auto-materializes slots) |
| `DELETE /coaches/time-windows/{windowId}` | Delete time window + its unbooked slots |
| `GET /coaches/{id}/slots?date=YYYY-MM-DD` | Get materialized slots for a specific date |

---

## Desired End State

After this plan:
1. `src/lib/api.ts` perfectly mirrors the current API contract
2. `/coaches/[id]` lets a CLIENT pick a date, see available slots, and book one or more consecutive slots
3. `/coach/availability` lets a COACH manage **time windows** (create/delete) and inspect slots by date
4. All booking-related pages display `endsAt` where relevant
5. The `booked` boolean is replaced everywhere with the `status` enum

### Verification
- Browse to `/coaches/[id]` as CLIENT → date picker appears → select date → slots load → pick slot(s) → book → redirected to booking detail
- Browse to `/coach/availability` as COACH → list of time windows → create form with 6 fields → delete a window → inspect slots for a date
- Create a booking as CLIENT → booking detail shows correct `startsAt` and `endsAt`
- No runtime 404/400 errors in browser network tab

---

## What We Are NOT Doing

- Not implementing multi-slot selection UI (client picks a single slot; `availabilityIds` will be `[slotId]`). Multi-slot is a UX enhancement for a future plan.
- Not building a notifications preferences UI (email/WhatsApp settings).
- Not adding `pricePerUnit` display anywhere in the UI (field exists but is optional; ignore for now).
- Not redesigning the landing page or global nav.
- Not adding tests (no test suite configured).

---

## Implementation Approach

Changes flow in dependency order:
1. **`api.ts`** — single source of truth; fix all types and methods first.
2. **`/coaches/[id]`** — fix client-facing availability browsing and booking creation.
3. **`/coach/availability`** — redesign for time windows.
4. **`/bookings/[id]`** — display `endsAt`, fix any `booked` references.

Each phase is small and independently deployable.

---

## Phase 1 — Update `src/lib/api.ts`

### Overview
Replace the three removed availability methods with the four new ones, fix type shapes.

### Changes Required

#### 1. Types

**File**: `src/lib/api.ts`

Remove `AvailabilitySlot` and replace with the two new shapes:

```typescript
// REMOVE:
interface AvailabilitySlot {
  id: number;
  startsAt: string;
  endsAt: string;
  booked: boolean;
}

// ADD:
type AvailabilityStatus = 'AVAILABLE' | 'BOOKED' | 'EXPIRED';

interface AvailabilitySlot {           // returned by GET /coaches/{id}/slots
  id: number;
  startsAt: string;
  endsAt: string;
  status: AvailabilityStatus;
}

interface TimeWindowRequest {
  startDate: string;         // YYYY-MM-DD
  endDate: string;           // YYYY-MM-DD
  dailyStartTime: string;    // HH:mm
  dailyEndTime: string;      // HH:mm
  unitOfWorkMinutes: number; // e.g. 30
  pricePerUnit?: number;     // optional
  priority: number;          // default 0
}

interface TimeWindowResponse {
  id: number;
  startDate: string;
  endDate: string;
  dailyStartTime: string;
  dailyEndTime: string;
  unitOfWorkMinutes: number;
  pricePerUnit?: number;
  priority: number;
}
```

Update `BookingResponse`:

```typescript
interface BookingResponse {
  id: number;
  clientId: number;
  clientName: string;
  coachId: number;
  coachName: string;
  availabilityIds: number[];   // NEW — was missing
  startsAt: string;
  endsAt: string;              // NEW — was missing
  status: BookingStatus;
  notes: string | null;
  createdAt: string;
}
```

Update `CreateBookingRequest` (implicit inline in `createBooking`):

```typescript
// Old call:
createBooking(payload: { availabilityId: number; notes?: string })

// New call:
createBooking(payload: { availabilityIds: number[]; notes?: string })
```

#### 2. Availability methods

**File**: `src/lib/api.ts`

Remove:
```typescript
getCoachAvailability(coachId: number): Promise<AvailabilitySlot[]>
addAvailability(coachId: number, payload: { startsAt: string; endsAt: string }): Promise<AvailabilitySlot>
deleteAvailability(slotId: number): Promise<void>
```

Add:
```typescript
// Time window management (COACH)
getTimeWindows(coachId: number): Promise<TimeWindowResponse[]>
  → GET /coaches/{coachId}/time-windows

createTimeWindow(coachId: number, payload: TimeWindowRequest): Promise<TimeWindowResponse>
  → POST /coaches/{coachId}/time-windows

deleteTimeWindow(windowId: number): Promise<void>
  → DELETE /coaches/time-windows/{windowId}

// Slot browsing (public)
getSlotsByDate(coachId: number, date: string): Promise<AvailabilitySlot[]>
  → GET /coaches/{coachId}/slots?date={date}
```

### Success Criteria

#### Automated Verification
- [x] TypeScript compiles with no errors: `npm run check`

#### Manual Verification
- [x] No TypeScript red squiggles in IDE for api.ts

---

## Phase 2 — Fix `/coaches/[id]` (Client Booking Page)

### Overview
Replace the flat availability list with a date-picker + slot list. Booking creation uses `availabilityIds` array.

### Changes Required

#### 1. State variables

**File**: `src/routes/coaches/[id]/+page.svelte`

Add:
```typescript
let selectedDate = $state<string>('');       // YYYY-MM-DD, starts empty
let slots = $state<AvailabilitySlot[]>([]);
let slotsLoading = $state(false);
let slotsError = $state<string | null>(null);
```

Remove:
```typescript
let availability = $state<AvailabilitySlot[]>([]);   // old flat load
```

#### 2. Load slots on date change

Replace the `$effect` that loaded all availability with:

```typescript
$effect(() => {
  if (!selectedDate) { slots = []; return; }
  slotsLoading = true; slotsError = null;
  api.getSlotsByDate(coachId, selectedDate)
    .then(data => { slots = data; })
    .catch(e => { slotsError = e.message; })
    .finally(() => { slotsLoading = false; });
});
```

#### 3. Booking creation

Update call:
```typescript
// old:
await api.createBooking({ availabilityId: slot.id, notes: bookingNotes });
// new:
await api.createBooking({ availabilityIds: [slot.id], notes: bookingNotes || undefined });
```

#### 4. Template

Replace the availability list section with:

```svelte
<!-- Date picker -->
<label for="slot-date">Select a date</label>
<input
  id="slot-date"
  type="date"
  bind:value={selectedDate}
  min={new Date().toISOString().slice(0, 10)}
/>

<!-- Slot list -->
{#if selectedDate}
  {#if slotsLoading}
    <p>Loading slots…</p>
  {:else if slotsError}
    <p class="error">{slotsError}</p>
  {:else if slots.length === 0}
    <p>No slots available on this date.</p>
  {:else}
    <table>
      <thead><tr><th>Starts</th><th>Ends</th><th></th></tr></thead>
      <tbody>
        {#each slots as slot}
          <tr>
            <td>{formatTime(slot.startsAt)}</td>
            <td>{formatTime(slot.endsAt)}</td>
            <td>
              {#if slot.status === 'AVAILABLE' && $role === 'CLIENT'}
                <button onclick={() => bookSlot(slot.id)}>Book</button>
              {:else if slot.status !== 'AVAILABLE'}
                <span class="badge">{slot.status}</span>
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
{/if}
```

Replace any `slot.booked` checks with `slot.status !== 'AVAILABLE'`.

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [x] As a CLIENT: navigate to a coach's page → no slots shown initially
- [x] Pick today's date → slots load from API
- [x] Pick a date with no slots → "No slots available" message
- [x] Click "Book" on an AVAILABLE slot → booking created → redirected to `/bookings/{id}`
- [x] BOOKED/EXPIRED slots show badge, no Book button

**Implementation Note**: Pause here for manual confirmation before Phase 3.

---

## Phase 3 — Redesign `/coach/availability` (Time Window Management)

### Overview
Complete redesign. Old individual-slot UI is replaced with a time window create/delete form plus a slot inspector.

### Changes Required

#### 1. State variables

**File**: `src/routes/coach/availability/+page.svelte`

Remove all old slot state. Add:

```typescript
// Time windows
let windows = $state<TimeWindowResponse[]>([]);
let windowsLoading = $state(true);
let windowsError = $state<string | null>(null);

// Create form
let form = $state({
  startDate: '',
  endDate: '',
  dailyStartTime: '',
  dailyEndTime: '',
  unitOfWorkMinutes: 30,
  priority: 0
});
let creating = $state(false);
let createError = $state<string | null>(null);

// Slot inspector
let inspectDate = $state('');
let inspectedSlots = $state<AvailabilitySlot[]>([]);
let inspectLoading = $state(false);
```

#### 2. Load & mutations

```typescript
async function loadWindows() {
  windowsLoading = true;
  windows = await api.getTimeWindows($userId!);
  windowsLoading = false;
}

async function createWindow() {
  creating = true; createError = null;
  try {
    const w = await api.createTimeWindow($userId!, {
      ...form,
      unitOfWorkMinutes: Number(form.unitOfWorkMinutes),
      priority: Number(form.priority)
    });
    windows = [...windows, w];
    // reset form
    form = { startDate: '', endDate: '', dailyStartTime: '', dailyEndTime: '',
             unitOfWorkMinutes: 30, priority: 0 };
  } catch(e: any) {
    createError = e.message;
  } finally {
    creating = false;
  }
}

async function deleteWindow(id: number) {
  await api.deleteTimeWindow(id);
  windows = windows.filter(w => w.id !== id);
  // clear inspector if needed
}
```

#### 3. Template structure

```
Page title: "Availability"

Section 1: "Time Windows"
  - Table of existing time windows:
    columns: Date Range | Daily Hours | Slot Duration | Actions
    row: "Mar 1 – Mar 31" | "09:00 – 17:00" | "30 min" | [Delete]
  - Empty state: "No time windows defined."

Section 2: "Create Time Window"
  - Form fields (inline label+input pairs):
    - Start date (date input)
    - End date (date input)
    - Daily start time (time input)
    - Daily end time (time input)
    - Slot duration in minutes (number input, min=5)
    - Priority (number input, default 0)
  - [Create] button

Section 3: "Inspect Slots"
  - Date picker
  - On date change: load slots via getSlotsByDate
  - Table: Starts | Ends | Status
```

#### 4. Delete confirmation

For delete, show inline confirmation:

```svelte
{#if confirmDeleteId === window.id}
  <span>Delete this window and its unbooked slots?</span>
  <button onclick={() => deleteWindow(window.id)}>Confirm</button>
  <button onclick={() => confirmDeleteId = null}>Cancel</button>
{:else}
  <button onclick={() => confirmDeleteId = window.id}>Delete</button>
{/if}
```

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [x] As COACH: navigate to `/coach/availability`
- [x] No existing time windows → "No time windows defined" message shown
- [x] Fill create form with valid date range + times + duration → submit → window appears in list
- [x] Inspect slots for a date within the window → materialized slots appear
- [x] Delete a window → confirmation step → window removed from list
- [x] Try to delete a window that has a booking → API returns 409 → error shown

**Implementation Note**: Pause here for manual confirmation before Phase 4.

---

## Phase 4 — Fix `/bookings/[id]` (Booking Detail)

### Overview
Small update: display `endsAt`, remove any leftover `booked` field references.

### Changes Required

**File**: `src/routes/bookings/[id]/+page.svelte`

1. Add `endsAt` to the session time display:

```svelte
<!-- Before (shows only startsAt): -->
<dd>{formatDateTime(booking.startsAt)}</dd>

<!-- After: -->
<dd>{formatDateTime(booking.startsAt)} – {formatTime(booking.endsAt)}</dd>
```

2. Verify no references to the old `booking.availabilityId` (singular) exist; if so, remove or update to `booking.availabilityIds`.

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [x] Booking detail page shows both start and end time
- [x] No console errors about undefined fields

---

## Testing Strategy

### Manual Testing Steps (end-to-end)

1. Register as COACH → approve via admin → log in
2. Go to `/coach/availability` → create a time window for the next 7 days, 09:00–17:00, 30-min slots
3. Inspect a date in that range → confirm slots appear
4. Register as CLIENT → log in → browse to the coach's profile page
5. Pick the same date → slots appear → book one → confirm redirect to booking detail
6. As COACH → go to `/bookings` → open the booking → approve it
7. As CLIENT → chat with coach; verify messages appear
8. As COACH → delete the time window (if no bookings) → slots gone on inspect
9. Attempt to delete a window with bookings → error displayed

---

## References

- API source: `../turtle-api/src/main/java/turtle/`
- Frontend source: `src/`
- Key API files:
  - `CoachResource.java` — `/coaches` endpoints
  - `BookingResource.java` — `/bookings` endpoints
  - `TimeWindowService.java` — slot materialization logic
  - `dto/TimeWindowRequest.java`, `dto/AvailabilityResponse.java`
