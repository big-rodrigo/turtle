# Design System Compliance Implementation Plan

## Overview

The frontend has 93 design system violations across 11 pages: 42 rounded-corner violations (spec requires `border-radius: 0` everywhere), 51 hardcoded color violations (spec requires CSS variables), missing CSS variable infrastructure, and inconsistent component styling. This plan brings every page and component into full compliance with CLAUDE.md.

---

## Current State Analysis

- `app.css` contains only `@import "tailwindcss"` — no CSS variables, no resets
- All pages use Tailwind's `rounded-lg` / `rounded-full` classes exclusively
- Primary buttons use `bg-black` instead of the spec's primary blue (`#1a3ecf`)
- Inputs use `focus:ring-black` instead of `--color-primary`
- Error alerts use `bg-red-50 text-red-700` instead of danger variables
- Status badges use `rounded-full` with hardcoded Tailwind colors
- Status color mapping (`statusColor` object) duplicated in 3 files
- No "approve/success" color defined in spec — green (`bg-green-600`) is used ad-hoc

---

## Desired End State

- `app.css` defines all CSS custom properties + a `border-radius: 0` global reset
- Zero instances of `rounded-lg`, `rounded-full`, or any `rounded-*` Tailwind class
- All buttons use `--color-primary` (primary), `--color-danger` (danger), or `--color-success` (new approve action)
- All input focus rings use `--color-primary`
- Error alerts use `--color-danger` family
- Badges have sharp edges, `2px 8px` padding, uppercase text, correct status colors
- A single `src/lib/status.ts` centralizes status color/label logic
- Every page type-checks cleanly with `npm run check`

### New Directives (extending CLAUDE.md)

These cover cases not specified in CLAUDE.md and must be honored alongside existing rules:

| Token | Value | Usage |
|-------|-------|-------|
| `--color-success` | `#1a7a3c` | Approve/positive actions (not in original palette) |
| `--color-success-dark` | `#136030` | Hover state for success buttons |
| `--color-success-light` | `#e6f4ec` | Success backgrounds |

**Status badge colors:**

| Status | Background | Text |
|--------|-----------|------|
| `PENDING` | `#fef9e7` (amber-50 equiv) | `#92600a` |
| `APPROVED` | `--color-success-light` | `--color-success` |
| `REJECTED` | `--color-danger-light` | `--color-danger` |
| `CANCELLED` | `--color-gray-100` | `--color-gray-600` |

Define as CSS custom properties:
```css
--status-pending-bg: #fef9e7;
--status-pending-text: #92600a;
--status-approved-bg: var(--color-success-light);
--status-approved-text: var(--color-success);
--status-rejected-bg: var(--color-danger-light);
--status-rejected-text: var(--color-danger);
--status-cancelled-bg: var(--color-gray-100);
--status-cancelled-text: var(--color-gray-600);
```

**Chat bubble colors (new directive):**
- Own messages: background `--color-primary`, text `--color-white`
- Other messages: background `--color-gray-100`, text `--color-black`

---

## What We're NOT Doing

- No Svelte component extraction (Button, Input, Badge, etc.) — fixes are inline per page to minimize scope
- No dark mode
- No design changes beyond what spec requires
- No routing or API changes
- No test suite additions

---

## Implementation Approach

Work file-by-file starting with the foundation (app.css), then fix every page top-down. Each phase is independently verifiable with `npm run check`.

---

## Phase 1: CSS Foundation

### Overview
Establish CSS variable infrastructure and global resets in `app.css`. This is a prerequisite for all other phases.

### Changes Required

**File**: `src/app.css`

Replace the single-line import with a full design token setup:

```css
@import "tailwindcss";

/* ─── Design Tokens ──────────────────────────────────── */
:root {
  --color-primary:       #1a3ecf;
  --color-primary-dark:  #122db8;
  --color-primary-light: #e8edfc;

  --color-danger:        #c0152c;
  --color-danger-dark:   #a01025;
  --color-danger-light:  #fbeaec;

  --color-success:       #1a7a3c;
  --color-success-dark:  #136030;
  --color-success-light: #e6f4ec;

  --color-black:    #0d0d0d;
  --color-white:    #ffffff;

  --color-gray-50:  #f7f7f8;
  --color-gray-100: #ececee;
  --color-gray-200: #d8d8dc;
  --color-gray-400: #9898a0;
  --color-gray-600: #5a5a62;

  /* Status badge tokens */
  --status-pending-bg:    #fef9e7;
  --status-pending-text:  #92600a;
  --status-approved-bg:   var(--color-success-light);
  --status-approved-text: var(--color-success);
  --status-rejected-bg:   var(--color-danger-light);
  --status-rejected-text: var(--color-danger);
  --status-cancelled-bg:  var(--color-gray-100);
  --status-cancelled-text: var(--color-gray-600);
}

/* ─── Global Resets ──────────────────────────────────── */
*,
*::before,
*::after {
  border-radius: 0 !important;
  box-sizing: border-box;
}

body {
  font-family: 'Inter', system-ui, sans-serif;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--color-black);
  background: var(--color-white);
}
```

> **Note on `!important`**: Using `!important` on `border-radius: 0` is the fastest safeguard while removing per-component `rounded-*` classes. Once all classes are removed (Phase 2+), the `!important` can be dropped.

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes
- [x] `npm run build` passes with no errors

#### Manual Verification
- [ ] Browser dev tools confirm `border-radius: 0` on any button/input on any page
- [ ] CSS variables visible in dev tools `:root` computed styles

---

## Phase 2: Centralize Status Color Logic

### Overview
Create `src/lib/status.ts` to eliminate the duplicated `statusColor` object pattern across 3 pages.

### Changes Required

**File**: `src/lib/status.ts` (new file)

```typescript
export const statusBadgeClass: Record<string, string> = {
  PENDING:   'status-pending',
  APPROVED:  'status-approved',
  REJECTED:  'status-rejected',
  CANCELLED: 'status-cancelled',
};

export const statusLabel: Record<string, string> = {
  PENDING:   'Pending',
  APPROVED:  'Approved',
  REJECTED:  'Rejected',
  CANCELLED: 'Cancelled',
};
```

Add to `src/app.css` (after the token block):

```css
/* ─── Status Badge Classes ───────────────────────────── */
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-radius: 0;
}
.status-pending   { background: var(--status-pending-bg);   color: var(--status-pending-text); }
.status-approved  { background: var(--status-approved-bg);  color: var(--status-approved-text); }
.status-rejected  { background: var(--status-rejected-bg);  color: var(--status-rejected-text); }
.status-cancelled { background: var(--status-cancelled-bg); color: var(--status-cancelled-text); }
```

Re-export from `src/lib/index.ts`:
```typescript
export { statusBadgeClass, statusLabel } from './status';
```

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

---

## Phase 3: Fix `+layout.svelte` (Navigation)

### Overview
The nav header contains a rounded sign-in button and hardcoded black/gray colors. Fix it to use primary blue and sharp edges.

### Changes Required

**File**: `src/routes/+layout.svelte`

- Remove `rounded-lg` from sign-in button → sharp edges (covered by Phase 1 reset, but remove the class)
- Change `bg-black hover:bg-gray-800` → `bg-[var(--color-primary)] hover:bg-[var(--color-primary-dark)]`
- Change `text-gray-600` nav text → `text-[var(--color-gray-600)]`
- Ensure max-width container uses `max-w-[1200px] mx-auto px-6`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Nav bar shows blue sign-in button
- [ ] Nav links are styled with correct gray

---

## Phase 4: Fix Auth Pages (`/login`, `/register`)

### Overview
Both pages have rounded inputs, rounded buttons, wrong focus rings, and hardcoded error colors.

### Changes Required (apply to both files)

**Files**: `src/routes/login/+page.svelte`, `src/routes/register/+page.svelte`

1. **Remove all `rounded-lg`** from inputs, buttons, error alerts
2. **Buttons**: replace `bg-black hover:bg-gray-800` with `bg-[var(--color-primary)] hover:bg-[var(--color-primary-dark)]`
3. **Focus rings on inputs**: replace `focus:ring-black` with Tailwind arbitrary `focus:outline-[2px] focus:outline-[var(--color-primary)]` or use inline style `style="outline: 2px solid var(--color-primary)"`
4. **Error alert**: replace `bg-red-50 text-red-700` with `bg-[var(--color-danger-light)] text-[var(--color-danger)] border border-[var(--color-danger)]`
5. **Input height**: add `h-[40px]` to all single-line inputs
6. **Input border**: add `border border-[var(--color-gray-200)]`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Login/register forms show sharp-edged inputs with blue focus ring
- [ ] Submit button is blue
- [ ] Error message renders with red border/background from design system

---

## Phase 5: Fix Landing Page (`/`)

### Overview
Two rounded buttons on the landing page need to be made sharp and colored correctly.

### Changes Required

**File**: `src/routes/+page.svelte`

- Remove `rounded-lg` from CTA buttons
- Change primary CTA `bg-black hover:bg-gray-800` → primary blue
- `text-gray-500` subtitle → `text-[var(--color-gray-600)]`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Landing page CTA buttons are blue, sharp-edged

---

## Phase 6: Fix Coach Pages (`/coaches`, `/coaches/[id]`)

### Overview
Coach listing has a rounded card; coach detail has rounded slots, buttons, and error alert.

### Changes Required

**File**: `src/routes/coaches/+page.svelte`

- Remove `rounded-lg` from list item link
- `text-gray-500` / `text-gray-400` → CSS variable equivalents
- Hover `hover:bg-gray-50` → `hover:bg-[var(--color-primary-light)]`

**File**: `src/routes/coaches/[id]/+page.svelte`

- Remove all `rounded-lg`
- Book button: `bg-black hover:bg-gray-800` → primary blue
- Sign-in link button → secondary (white bg, border)
- Error alert → danger variables
- `text-red-600` status text → `text-[var(--color-danger)]`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Coach listing shows sharp item rows
- [ ] Coach profile shows sharp availability slots and blue book button

---

## Phase 7: Fix Bookings List (`/bookings`)

### Overview
Booking list has rounded cards, `rounded-full` status badges, and duplicated hardcoded status color object.

### Changes Required

**File**: `src/routes/bookings/+page.svelte`

- Remove duplicated `statusColor` object; import `statusBadgeClass` from `$lib/status`
- Replace badge element:
  ```svelte
  <!-- Before -->
  <span class="rounded-full px-2.5 py-0.5 text-xs font-medium {statusColor[booking.status]}">
  <!-- After -->
  <span class="status-badge {statusBadgeClass[booking.status]}">
  ```
- Remove `rounded-lg` from booking list item
- `text-gray-500` / `text-gray-400` → CSS variable values
- `hover:bg-gray-50` → `hover:bg-[var(--color-primary-light)]`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Status badges are sharp-edged, uppercase, correctly colored per status

---

## Phase 8: Fix Booking Detail (`/bookings/[id]`)

### Overview
Most violations are here: 9 rounded corners, 15 hardcoded colors, chat bubbles, all 3 button types.

### Changes Required

**File**: `src/routes/bookings/[id]/+page.svelte`

1. Remove duplicated `statusColor` object; use `statusBadgeClass` from `$lib`
2. Status badge: `rounded-full` → sharp, update classes
3. Remove `rounded-lg` from main card
4. Error alert → danger variables
5. **Approve button**: `bg-green-600 hover:bg-green-700` → `bg-[var(--color-success)] hover:bg-[var(--color-success-dark)]`
6. **Reject button**: `bg-red-600 hover:bg-red-700` → `bg-[var(--color-danger)] hover:bg-[var(--color-danger-dark)]`
7. **Cancel button**: `text-red-600 hover:bg-red-50` → `text-[var(--color-danger)] hover:bg-[var(--color-danger-light)]`
8. **Chat send button**: `bg-black hover:bg-gray-800` → primary blue
9. **Chat input**: `focus:ring-black` → primary focus, `rounded-lg` removed
10. **Chat bubbles**:
    - Own: `bg-black text-white` → `bg-[var(--color-primary)] text-[var(--color-white)]`
    - Other: `bg-gray-100 text-gray-900` → `bg-[var(--color-gray-100)] text-[var(--color-black)]`
    - Remove `rounded-lg` from both
    - Add `box-shadow: 0 1px 3px rgba(0,0,0,0.08)` (Low elevation)

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Approve = green button, Reject = red button, Cancel = red text secondary
- [ ] Chat bubbles: own messages blue, other messages gray, both sharp-edged
- [ ] Status badge correct color and shape

---

## Phase 9: Fix Coach Availability (`/coach/availability`)

### Overview
Form with 7 rounded corners, 6 hardcoded colors.

### Changes Required

**File**: `src/routes/coach/availability/+page.svelte`

- Remove all `rounded-lg` (form container, error alert, 2 inputs, button, slot items, delete button)
- Add button: primary blue
- Delete button: `text-red-600 hover:bg-red-50` → danger variables
- Error alert: danger variables
- `focus:ring-black` → primary focus
- `text-green-700` for slot active status → `text-[var(--color-success)]`
- Input heights 40px, proper borders

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Availability form is sharp-edged, blue submit button
- [ ] Slot list items are sharp
- [ ] Delete button uses danger red

---

## Phase 10: Fix Admin Coaches (`/admin/coaches`)

### Overview
Admin page has `rounded-full` filter buttons, rounded cards, `rounded-full` on status badge, hardcoded approve/reject button colors.

### Changes Required

**File**: `src/routes/admin/coaches/+page.svelte`

- Remove `rounded-full` from filter buttons; make them secondary-style (border, no fill for inactive; primary fill for active)
- Remove `rounded-lg` from error alert and coach item cards
- Status badge: remove `rounded-full`, use `status-badge` + `statusBadgeClass`
- Approve button: `bg-green-600` → success color
- Reject button: `bg-red-600` → danger color
- Error alert: danger variables
- Import `statusBadgeClass` from `$lib/status`

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

#### Manual Verification
- [ ] Filter buttons are sharp
- [ ] Approve = green, Reject = red (from design system, not Tailwind)
- [ ] Status badges correct color

---

## Phase 11: Fix Pending Approval (`/pending-approval`)

### Overview
Single rounded button to fix.

### Changes Required

**File**: `src/routes/pending-approval/+page.svelte`

- Remove `rounded-lg` from sign-out button
- Ensure sign-out is secondary button style (border, no fill) or danger

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes

---

## Phase 12: Final Audit & `!important` Cleanup

### Overview
Once all `rounded-*` classes are removed, the `!important` override in the global reset can be dropped.

### Changes Required

1. Run `npm run check` and `npm run build` — must pass clean
2. Search for any remaining `rounded-` class with: `grep -r "rounded-" src/`
3. If zero results, remove `!important` from `border-radius: 0` in `app.css`
4. Final visual pass in browser across all routes

### Success Criteria

#### Automated Verification
- [x] `npm run check` passes
- [x] `npm run build` passes
- [x] `grep -r "rounded-" src/` returns zero results

#### Manual Verification
- [ ] `/` — Landing: blue CTA buttons, sharp edges
- [ ] `/login` — Login: sharp inputs/button, blue focus ring
- [ ] `/register` — Register: same as login
- [ ] `/coaches` — Coach list: sharp rows, hover in primary-light
- [ ] `/coaches/[id]` — Coach profile: sharp slots, blue book button
- [ ] `/bookings` — Bookings list: sharp rows, correct status badges
- [ ] `/bookings/[id]` — Booking detail: all buttons correct color, chat correct
- [ ] `/coach/availability` — Availability: sharp form, blue submit, red delete
- [ ] `/admin/coaches` — Admin: sharp filter tabs, correct approve/reject
- [ ] `/pending-approval` — Pending: sharp button

---

## Testing Strategy

### Automated:
- `npm run check` after each phase
- `npm run build` before final sign-off
- `grep -r "rounded-" src/` to confirm zero violations

### Manual Testing Steps:
1. Navigate to each route and verify sharp edges on all interactive elements
2. Click inputs, confirm blue 2px focus ring appears
3. Submit a form with validation error — confirm red border/background
4. View booking with PENDING status — confirm amber badge
5. View booking with APPROVED/REJECTED — confirm green/red badges
6. Open a booking as coach — confirm green Approve, red Reject buttons
7. Send a chat message — confirm own messages blue, received gray
8. Admin page: approve a coach — confirm green button, reject = red

---

## References

- Design spec: `CLAUDE.md`
- Audit source: all files in `src/routes/`
- Key files: `src/app.css`, `src/lib/index.ts`, `src/lib/status.ts` (new)
