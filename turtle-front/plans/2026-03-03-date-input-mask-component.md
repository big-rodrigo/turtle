# DateInput Masked Component — Implementation Plan

## Overview

Replace all 4 native `type="date"` inputs in the frontend with a reusable `DateInput.svelte` component that uses a `dd/mm/yyyy` input mask. The component's bound value remains in `YYYY-MM-DD` format for API compatibility, while the user-facing display and entry is always `dd/mm/yyyy`.

---

## Current State Analysis

Four native `<input type="date">` elements exist, spread across two routes:

| File | Variable | Extra props |
|------|----------|-------------|
| `src/routes/coach/availability/+page.svelte:240` | `form.startDate` | `required` |
| `src/routes/coach/availability/+page.svelte:250` | `form.endDate` | `required` |
| `src/routes/coach/availability/+page.svelte:308` | `inspectDate` | — |
| `src/routes/coaches/[id]/+page.svelte:61` | `selectedDate` | `min={new Date().toISOString().slice(0, 10)}` |

All bound values are `YYYY-MM-DD` strings (ISO date format), used directly in API calls.
No reusable component library exists — this will be the **first shared component** in `src/lib/components/`.

---

## Desired End State

- A single `src/lib/components/DateInput.svelte` component is the canonical way to capture a date across the entire app.
- Users type digits freely; slashes are auto-inserted at positions 2 and 5 (`dd/mm/yyyy`).
- Deleting works naturally (backspace removes characters, including auto-inserted slashes).
- The `value` bindable prop stays in `YYYY-MM-DD` so callers need zero migration of their API/state logic.
- The `min` prop (optional, in `YYYY-MM-DD` format) prevents selecting past dates where needed.
- CLAUDE.md documents the component for future contributors.

### Verification
- Open `/coach/availability` → both "Start date" and "End date" fields accept `dd/mm/yyyy` input with auto-slashes; form submits correctly.
- Open `/coaches/[id]` → date picker enforces today as minimum; selecting a date loads available slots.
- Pasting a date, deleting characters, and tabbing away all behave sensibly.

---

## What We're NOT Doing

- No third-party masking library (e.g. `imask`, `cleave.js`) — custom logic keeps the bundle lean.
- No calendar/popover picker — text entry only.
- No `type="date"` fallback — the component fully replaces the native input.
- No dark mode or additional variants beyond what the design system already defines.
- No changes to API payload format — `YYYY-MM-DD` stays as-is.

---

## Implementation Approach

Build the mask as pure Svelte 5 event handler logic on a plain `<input type="text">`:

1. **`oninput` handler** — after every keystroke, strip non-digits, enforce max 8 digits, re-insert slashes at positions 2 and 5, clamp day to 01-31 and month to 01-12, then update the cursor position.
2. **`onblur` handler** — when the field loses focus, if the display string is a complete valid date (`dd/mm/yyyy`), convert it to `YYYY-MM-DD` and emit via `bind:value`. If incomplete/invalid, clear the bound value (set to `''`).
3. **Reactive initialisation** — when `value` (YYYY-MM-DD) changes externally, convert it to `dd/mm/yyyy` for the display string.
4. **`min` validation** — on blur, if `min` is provided, compare the parsed date and set a custom validity message if the date is before `min`.

---

## Phase 1 — Create the Component

### Overview
Create `src/lib/components/DateInput.svelte` and export it from `src/lib/index.ts`.

### Changes Required

#### 1. New component file
**File**: `src/lib/components/DateInput.svelte`

```svelte
<script lang="ts">
  interface Props {
    id?: string;
    value?: string;          // YYYY-MM-DD; bindable
    min?: string;            // YYYY-MM-DD optional minimum date
    required?: boolean;
    class?: string;
  }

  let { id, value = $bindable(''), min, required = false, class: extraClass = '' }: Props = $props();

  // ── helpers ─────────────────────────────────────────────────────────────
  function toDisplay(iso: string): string {
    if (!iso || iso.length < 10) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  function toISO(display: string): string | null {
    const parts = display.split('/');
    if (parts.length !== 3) return null;
    const [d, m, y] = parts;
    if (d.length !== 2 || m.length !== 2 || y.length !== 4) return null;
    const iso = `${y}-${m}-${d}`;
    const date = new Date(iso);
    if (isNaN(date.getTime())) return null;
    // re-format to catch invalid days like 31/02
    const check = date.toISOString().slice(0, 10);
    return check === iso ? iso : null;
  }

  // ── display state ────────────────────────────────────────────────────────
  let display = $state(toDisplay(value));

  $effect(() => {
    // sync inbound changes (e.g. form reset)
    const expected = toDisplay(value);
    if (display !== expected) display = expected;
  });

  // ── mask logic ───────────────────────────────────────────────────────────
  function handleInput(e: Event) {
    const input = e.target as HTMLInputElement;
    const raw = input.value.replace(/\D/g, '').slice(0, 8);

    let masked = '';
    for (let i = 0; i < raw.length; i++) {
      if (i === 2 || i === 4) masked += '/';
      masked += raw[i];
    }

    display = masked;
    input.value = masked;

    // partial input → clear bound value
    if (masked.length < 10) {
      if (value !== '') value = '';
    }
  }

  function handleBlur(e: Event) {
    const input = e.target as HTMLInputElement;
    const iso = toISO(display);

    if (!iso) {
      display = toDisplay(value); // revert to last valid or empty
      input.setCustomValidity(display === '' && required ? 'Please enter a date.' : '');
      return;
    }

    if (min && iso < min) {
      input.setCustomValidity(`Date must be on or after ${toDisplay(min)}.`);
    } else {
      input.setCustomValidity('');
    }

    value = iso;
    display = toDisplay(iso);
  }
</script>

<input
  {id}
  type="text"
  inputmode="numeric"
  placeholder="dd/mm/yyyy"
  maxlength="10"
  {required}
  value={display}
  oninput={handleInput}
  onblur={handleBlur}
  class="h-[40px] border border-[var(--color-gray-200)] bg-[var(--color-white)] px-3 py-2 text-sm focus:outline-[2px] focus:outline-[var(--color-primary)] {extraClass}"
/>
```

#### 2. Export from lib index
**File**: `src/lib/index.ts`
**Change**: add export for the new component alongside existing exports.

```typescript
export { default as DateInput } from './components/DateInput.svelte';
```

### Success Criteria

#### Automated Verification:
- [x] Type-check passes: `npm run check`

#### Manual Verification:
- [ ] Typing `01022025` produces `01/02/2025` with slashes auto-inserted
- [ ] Backspace after full date removes characters including slashes naturally
- [ ] Tabbing away from an incomplete entry clears the field (no partial ISO value emitted)
- [ ] Tabbing away from an invalid date (e.g. `31/02/2025`) reverts the display and does not set value
- [ ] Passing `min` and entering an earlier date shows a browser validation message on submit

---

## Phase 2 — Replace All Native Date Inputs

### Overview
Swap every `<input type="date">` for `<DateInput>`, keeping the same `bind:value`, `id`, `required`, and `min` props.

### Changes Required

#### 1. Coach availability page — 3 replacements
**File**: `src/routes/coach/availability/+page.svelte`

Add import at the top of the `<script>` block:
```typescript
import { DateInput } from '$lib';
```

Replace at line 237–243:
```svelte
<DateInput id="startDate" bind:value={form.startDate} required class="w-full" />
```

Replace at line 247–253:
```svelte
<DateInput id="endDate" bind:value={form.endDate} required class="w-full" />
```

Replace at line 305–310:
```svelte
<DateInput id="inspect-date" bind:value={inspectDate} />
```

#### 2. Coach booking page — 1 replacement
**File**: `src/routes/coaches/[id]/+page.svelte`

Add import:
```typescript
import { DateInput } from '$lib';
```

Replace at line 58–64:
```svelte
<DateInput id="slot-date" bind:value={selectedDate} min={new Date().toISOString().slice(0, 10)} />
```

### Success Criteria

#### Automated Verification:
- [x] Type-check passes: `npm run check`
- [x] Build succeeds: `npm run build`

#### Manual Verification:
- [ ] `/coach/availability` → create time window with masked date inputs, form submits without error
- [ ] `/coach/availability` → inspect slots section filters by masked date input
- [ ] `/coaches/[id]` → selecting a date with the masked input loads available slots
- [ ] Entering a past date in `/coaches/[id]` shows a validation error (via `min`)
- [ ] No visual regressions — component matches design system (40px height, 1px border, 2px primary focus ring, no border-radius)

**Implementation Note**: After completing this phase and all automated checks pass, confirm manually that each use case above works before proceeding.

---

## Phase 3 — Update CLAUDE.md

### Overview
Document the `DateInput` component in `CLAUDE.md` so future contributors know it exists and how to use it.

### Changes Required

**File**: `CLAUDE.md`
**Location**: after the "Key files" section, add a new "### Shared components" subsection:

```markdown
### Shared components

- `src/lib/components/DateInput.svelte` — Masked date input. Bind `value` (a `YYYY-MM-DD` string). Displays and accepts input as `dd/mm/yyyy`. Supports `id`, `required`, `min` (YYYY-MM-DD), and `class` props. **Always use this instead of `<input type="date">`.**

  ```svelte
  <DateInput id="myDate" bind:value={isoDate} required />
  <DateInput id="fromDate" bind:value={from} min={new Date().toISOString().slice(0, 10)} />
  ```
```

### Success Criteria

#### Automated Verification:
- [x] CLAUDE.md is updated and committed alongside the component

---

## Testing Strategy

### Manual Testing Steps
1. Navigate to `/coach/availability`, open "Create Time Window".
2. Click "Start date" field — placeholder `dd/mm/yyyy` is visible.
3. Type `15` — field shows `15/`.
4. Type `03` — field shows `15/03/`.
5. Type `2026` — field shows `15/03/2026`.
6. Tab away — value is set, form can be submitted.
7. Clear field, type `31022026`, tab away — field reverts (invalid date), value is empty.
8. Navigate to `/coaches/[id]`, try entering a past date — validation message should appear on form submit.

---

## References

- Design system: `CLAUDE.md` — input spec (40px height, 1px border, 2px focus outline, no border-radius)
- Existing date inputs: `src/routes/coach/availability/+page.svelte:237,247,305` and `src/routes/coaches/[id]/+page.svelte:58`
