# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev          # Start dev server (http://localhost:5173)
npm run build        # Production build
npm run preview      # Preview production build
npm run check        # Type-check with svelte-check
npm run check:watch  # Type-check in watch mode
```

No test suite is configured yet.

## Styling Rules (irrevocable)

**`style=""` attributes are banned.** This is a hard rule with no exceptions anywhere in the codebase.

- Use **Tailwind utility classes** for all layout, spacing, typography, and color.
- Design tokens registered via `@theme` in `src/app.css` are available as Tailwind utilities: `text-primary`, `bg-surface-1`, `text-text-muted`, `bg-surface-border`, etc.
- For glow box-shadows use the custom utilities: `shadow-glow-primary`, `shadow-glow-danger`, `shadow-glow-success`.
- For text-shadow glows: `text-shadow-brand`, `text-shadow-primary-sm`, `text-shadow-primary-md`, `text-shadow-primary-lg`, `text-glow-primary`.
- Reusable component patterns live in `src/app.css` under `@layer components`: `.btn`, `.btn-primary`, `.btn-danger`, `.btn-success`, `.btn-secondary`, `.btn-ghost-danger`, `.btn-sm`, `.btn-lg`, `.page-wrapper`, `.page-centered`, `.auth-centered`, `.section-label`, `.page-title`, `.page-title-md`, `.page-title-lg`, `.panel-title`, `.section-heading`, `.form-label`, `.loading-text`, `.empty-text`, `.back-link`, `.list-row`, `.detail-text`, `.detail-label`, `.chat-list`, `.chat-msg-own`, `.chat-msg-other`, `.chat-bubble-own`, `.chat-bubble-other`, `.chat-meta`, `.slot-status`, `.slot-available`, `.slot-booked`, `.filter-btn`, `.filter-btn-active`, `.filter-btn-inactive`, `.text-primary-glow`, `.nav-header`, `.nav-inner`, `.brand-link`, `.nav-cta`, `.nav-logout`, `.page-main`.
- **Scoped `<style>` blocks** inside `.svelte` files are allowed **only** for component-internal structural styles (e.g. scoped hover states, pseudo-elements) that cannot be expressed with Tailwind utilities.
- **Dynamic single-property overrides** use Svelte's property shorthand — never a full `style=""` string:
  ```svelte
  style:opacity={loading ? 0.5 : 1}
  style:border-top={active ? '2px solid var(--color-primary)' : 'none'}
  ```

## Architecture

This is a **SvelteKit 2 + Svelte 5 + TypeScript + Tailwind CSS 4** frontend for the Turtle Coaching platform. It communicates with a Quarkus REST API running at `http://localhost:8080`.

### Key files

- `src/lib/api.ts` — Single `ApiClient` class (exported as `api`) that wraps all REST calls. All API types (`UserRole`, `BookingStatus`, response interfaces) are defined here. The base URL is hardcoded to `http://localhost:8080`.
- `src/lib/auth.ts` — Svelte stores for authentication state. `token` (writable store) persists the JWT in `localStorage` under the key `turtle_token`. Derived stores: `user` (parsed JWT payload), `role` (`UserRole | null`), `userId` (`number | null`). Calling `api.setToken()` is handled automatically by the auth store.
- `src/lib/index.ts` — Re-exports `api`, `token`, `user`, `role`, `userId` for convenient `$lib` imports.

### Shared components

- `src/lib/components/DateInput.svelte` — Masked date input. Bind `value` (a `YYYY-MM-DD` string). Displays and accepts input as `dd/mm/yyyy`. Supports `id`, `required`, `min` (YYYY-MM-DD), and `class` props. **Always use this instead of `<input type="date">`.**

  ```svelte
  <DateInput id="myDate" bind:value={isoDate} required />
  <DateInput id="fromDate" bind:value={from} min={new Date().toISOString().slice(0, 10)} />
  ```

### Auth & routing

The root `+page.svelte` redirects based on role:
- `CLIENT` / `COACH` → `/bookings`
- `COACH_PENDING` → `/pending-approval`
- `ADMIN` → `/admin/coaches`
- Unauthenticated → landing page

### User roles

| Role | Access |
|------|--------|
| `CLIENT` | Browse coaches, create/view bookings, chat |
| `COACH` | Manage availability, view/approve bookings, chat |
| `COACH_PENDING` | Awaits admin approval; redirected to `/pending-approval` |
| `ADMIN` | Approve/reject coach registrations at `/admin/coaches` |

### Route structure

```
/                     Landing / role-based redirect
/login                Login form
/register             Registration (CLIENT or COACH role)
/coaches              Coach listing
/coaches/[id]         Coach profile + availability slots + book
/bookings             Booking list (role-aware: client sees own, coach sees incoming)
/bookings/[id]        Booking detail + pay/confirm/reject/cancel + chat; handles ?payment= and ?action=pay params
/coach/availability   Coach's own availability management (add/delete slots)
/admin/coaches        Admin: list coaches by status, approve/reject
/pending-approval     Shown to COACH_PENDING users
```

### Booking & Payment Flow

Booking lifecycle (post V10 migration — MercadoPago Checkout Pro):
`PENDING_PAYMENT → AWAITING_COACH → CONFIRMED` (happy path)

| Status | Who acts | Frontend UI |
|--------|----------|-------------|
| `PENDING_PAYMENT` | CLIENT pays | "Pay Now" → `POST /bookings/{id}/payment/preference` → redirect to MercadoPago (or free session auto-advances) |
| `AWAITING_COACH` | COACH confirms | "Confirm Session" / "Reject" buttons; CLIENT sees cancel option |
| `CONFIRMED` | — | Chat unlocked for both parties |
| `REJECTED` | — | Auto-refund triggered by backend |
| `CANCELLED` | — | Refund triggered by backend if was `AWAITING_COACH` |

**Query params on `/bookings/[id]`:**
- `?action=pay` — appended by `coaches/[id]` after booking creation; triggers "payment required" CTA banner
- `?payment=success|failed|pending` — set by MercadoPago back-URL on return from checkout; shows result banner

Both params are read once on mount inside `$effect`, then stripped with `goto(replaceState: true)`.

**Free sessions:** If `pricePerUnit` is null/0, `createPaymentPreference()` returns `{ free: true }` and the booking is already in `AWAITING_COACH` — no redirect needed, the booking is re-fetched in place.

**`PaymentStatus` type** (on `BookingResponse.paymentStatus`, nullable until a payment record exists):
`PENDING | APPROVED | REJECTED | CANCELLED | REFUNDED | IN_MEDIATION`

### Svelte 5 patterns used

- Runes: `$state`, `$effect`, `$props`, `$derived`
- `{@render children()}` instead of `<slot />`
- Stores from `auth.ts` accessed with `$token`, `$role`, `$user` in templates

## Design System

### Color Palette

| Token | Value | Usage |
|-------|-------|-------|
| `--color-primary` | `#1a3ecf` | Royal blue — primary actions, links, focus rings |
| `--color-primary-dark` | `#122db8` | Hover/active state for primary |
| `--color-primary-light` | `#e8edfc` | Tinted backgrounds, selected row highlights |
| `--color-danger` | `#c0152c` | Royal red — destructive actions, errors, alerts |
| `--color-danger-dark` | `#a01025` | Hover/active state for danger |
| `--color-danger-light` | `#fbeaec` | Error backgrounds, alert fills |
| `--color-black` | `#0d0d0d` | Body text, headings |
| `--color-white` | `#ffffff` | Page background, card surfaces |
| `--color-gray-50` | `#f7f7f8` | Page background alternative, zebra rows |
| `--color-gray-100` | `#ececee` | Dividers, input backgrounds |
| `--color-gray-200` | `#d8d8dc` | Borders, disabled surfaces |
| `--color-gray-400` | `#9898a0` | Placeholder text, muted icons |
| `--color-gray-600` | `#5a5a62` | Secondary / caption text |

Light mode only — no dark mode support.

### Shape & Borders

- **No rounded corners** — `border-radius: 0` everywhere. All cards, inputs, buttons, badges, modals are sharp-edged.
- Borders use `1px solid` with `--color-gray-200` as the default border color.
- Use `2px solid --color-primary` for focus outlines (never `outline: none` without a visible replacement).

### Typography

- Font stack: `'Inter', system-ui, sans-serif` for UI; fallback to system sans-serif.
- Heading scale: `2rem / 1.5rem / 1.25rem / 1rem` (h1–h4), weight `700 / 600 / 600 / 500`.
- Body text: `1rem`, line-height `1.6`, color `--color-black`.
- Caption / helper text: `0.8125rem`, color `--color-gray-600`.
- **No italic text** in UI chrome — reserve italics for user-generated content only.

### Spacing & Layout

- Base spacing unit: `4px`. Use multiples: `4 / 8 / 12 / 16 / 24 / 32 / 48 / 64px`.
- Page max-width: `1200px`, centered with horizontal padding `24px`.
- Section separation via whitespace and `1px` horizontal rules — avoid nested card-in-card patterns.
- Dense information tables are preferred over large card grids.

### Components

- **Buttons**: sharp edges, `padding: 8px 20px`, `font-weight: 600`, `font-size: 0.9375rem`. Primary = blue fill white text; Secondary = white fill with `1px` border; Danger = red fill white text. Hover states darken the fill by ~10%.
- **Inputs & selects**: `border: 1px solid --color-gray-200`, background `--color-white`, focus ring `2px solid --color-primary`, no border-radius. Height `40px` for single-line.
- **Tables**: `border-collapse: collapse`, header row `background: --color-gray-50`, `font-weight: 600`. Row separator `1px solid --color-gray-100`. Hover row `background: --color-primary-light`.
- **Badges / status chips**: inline-block, `padding: 2px 8px`, `font-size: 0.75rem`, `font-weight: 600`, `letter-spacing: 0.04em`, uppercase text. Sharp edges, colored fill with contrasting text.
- **Modals / dialogs**: plain white box, `2px solid --color-gray-200` border, `box-shadow: 0 8px 32px rgba(0,0,0,0.12)`. No backdrop blur.
- **Dividers**: `1px solid --color-gray-100`, no decorative elements.

### Elevation & Shadow

Use box-shadows sparingly and only in these tiers:

| Level | Value | Use |
|-------|-------|-----|
| Low | `0 1px 3px rgba(0,0,0,0.08)` | Cards, inputs on hover |
| Mid | `0 4px 12px rgba(0,0,0,0.10)` | Dropdowns, popovers |
| High | `0 8px 32px rgba(0,0,0,0.12)` | Modals, drawers |

### Interaction & Motion

- Transition duration: `150ms ease` for color/background; `200ms ease` for transform/opacity.
- No decorative animations. Motion should be functional (loading spinners, transition between states).
- Loading states: use a simple inline spinner or skeleton rows — no full-page loaders.

### Iconography

- Use a single icon library consistently (e.g. Lucide). Size: `16px` inline, `20px` standalone actions.
- Icons are decorative only — always pair with a visible text label or `aria-label`.

---

## Cyberpunk Theme

The UI uses a dark cyberpunk aesthetic. The existing color palette and no-rounded-corners rules still apply; the changes below layer on top.

### Dark Surface Hierarchy

| Token | Value | Usage |
|-------|-------|-------|
| `--color-surface-0` | `#0d0d0d` | Page / body background |
| `--color-surface-1` | `#111318` | Card / panel background |
| `--color-surface-2` | `#191c23` | Modals, dropdowns, inputs, table headers |
| `--color-surface-border` | `rgba(26,62,207,0.25)` | Default border on dark surfaces |
| `--color-text-primary` | `#e8edfc` | Main text on dark backgrounds |
| `--color-text-secondary` | `#9898a0` | Secondary / label text |
| `--color-text-muted` | `#5a5a62` | Timestamps, captions, placeholder text |

### Glow Effects

| Token | Value | Usage |
|-------|-------|-------|
| `--glow-primary` | `0 0 8px rgba(26,62,207,0.6), 0 0 24px rgba(26,62,207,0.2)` | Soft blue — buttons, focused inputs |
| `--glow-primary-strong` | `0 0 12px rgba(26,62,207,0.9), 0 0 40px rgba(26,62,207,0.35)` | Hover state for primary buttons |
| `--glow-danger` | `0 0 8px rgba(192,21,44,0.6), 0 0 24px rgba(192,21,44,0.2)` | Danger / delete buttons |
| `--glow-success` | `0 0 8px rgba(26,122,60,0.6), 0 0 24px rgba(26,122,60,0.2)` | Approve / confirm buttons |

Apply glows via `box-shadow: var(--glow-primary)` on buttons and interactive elements.

### Monospace Font

- `--font-mono: 'JetBrains Mono', 'Fira Code', monospace`
- **Use for**: nav links, table headers, section labels, status chips, brand name, button text, form labels
- **Class**: `.mono` applies `font-family: var(--font-mono); letter-spacing: 0.04em;`
- Section labels follow the pattern: `SECTION // SUBSECTION` in uppercase mono at `0.65rem` with `letter-spacing: 0.18em`

### Panel Pattern

All content sections use the `.panel` utility class (dark bg + blue-tinted border). Never use white or light backgrounds for content surfaces.

```html
<div class="panel">...</div>               <!-- standard dark panel -->
<div class="panel panel-accent">...</div>  <!-- left glow-border accent -->
```

- `.panel` — `background: --color-surface-1`, `border: 1px solid --color-surface-border`, `padding: 24px`
- `.panel-accent` — adds `border-left: 2px solid --color-primary` with blue glow shadow
- On hover, panel border brightens: `rgba(26,62,207,0.45)`

### Status Badges (dark-adapted)

Status badge colors are now dark-mode variants with transparent backgrounds and bright text:

| Status | CSS class | Background | Text |
|--------|-----------|-----------|------|
| `PENDING_PAYMENT` | `status-pending` | `rgba(146,96,10,0.2)` | `#f0a840` |
| `AWAITING_COACH` | `status-awaiting` | `rgba(26,62,207,0.2)` | `#93c5fd` |
| `CONFIRMED` | `status-approved` | `rgba(26,122,60,0.2)` | `#4ade80` |
| `REJECTED` | `status-rejected` | `rgba(192,21,44,0.2)` | `#f87171` |
| `CANCELLED` | `status-cancelled` | `rgba(90,90,98,0.2)` | `#9898a0` |

### Animated Background

- **Component**: `src/lib/components/Background.svelte`
- **Technology**: Three.js WebGL
- **Effect**: ~120 floating points connected by proximity-based line segments (low-poly wireframe), rendered in royal blue (`#1a3ecf`)
- **Interaction**: mouse proximity pushes nearby points away (radius 160px)
- **Position**: `fixed`, `z-index: 0`, full-screen behind all content
- **Always include** in the root layout — never remove or conditionally hide

### Scan Lines

Applied as `body::after` pseudo-element: `repeating-linear-gradient` of 4px intervals at 3% opacity. Sits at `z-index: 9999`, `pointer-events: none`. This creates a subtle CRT texture visible on all surfaces.

### Nav Chrome

- Semi-transparent dark background: `rgba(13,13,19,0.88)` with `backdrop-filter: blur(10px)`
- Bottom border: `1px solid rgba(26,62,207,0.3)` with blue glow box-shadow
- Brand name: uppercase monospace, `--color-primary` color, text-shadow glow
- Nav links: uppercase monospace `0.72rem`, `letter-spacing: 0.1em`, hover brightens with text-shadow glow
- Main content: `position: relative; z-index: 1` to sit above the Three.js canvas

### Error Messages

Use the `.error-box` class instead of inline border/background for error messages:

```html
<p class="error-box">{errorMessage}</p>
```

This applies: `border: 1px solid rgba(192,21,44,0.4)`, `background: rgba(192,21,44,0.1)`, `color: #f87171`.

### Page Layout Convention

Every page should open with a section label in the mono style before the h1:

```html
<p class="section-label">SECTION // SUBSECTION</p>
<h1 class="page-title">Page Title</h1>
```

### Input / Select Styling

Inputs and selects are styled globally (see `app.css`):
- `background: --color-surface-2`, `border: 1px solid --color-surface-border`
- Focus: `border-color: --color-primary`, `box-shadow: var(--glow-primary)`, `outline: none`
- Always use `height: 40px` for single-line inputs; always set `width: 100%` within forms

### Table Styling

Tables are styled globally. Key points:
- `th`: monospace, `0.7rem`, uppercase, `letter-spacing: 0.08em`, `--color-surface-2` background
- `td`: `1px solid rgba(26,62,207,0.1)` bottom border
- Row hover: `background: rgba(26,62,207,0.07)`
- Always wrap tables inside a `.panel` div for padding and border context

---

## 3D Models

### Assets
GLTF models live under `static/models/` and are served at root by SvelteKit:

| Path | Description | Textures |
|------|-------------|----------|
| `static/models/squirt/scene.gltf` | Squirt (Finding Nemo turtle) | `textures/MAT_Squirt_baseColor.png` |
| `static/models/giant-turtle/scene.gltf` | Giant Armored Turtle Monster | `textures/tripo_mat_*` — baseColor, metallicRoughness, normal |

### Loading
Use `GLTFLoader` from Three.js (no extra package needed — it ships with `three`):
```ts
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
```

### Centering pattern (important)
Squirt's root joint has a significant world-space offset (~Y 0.764, ~90° rotation), so naively centering fails if scale is applied to `gltf.scene` directly. The correct pattern:

```ts
const box = new THREE.Box3().setFromObject(gltf.scene);
const center = box.getCenter(new THREE.Vector3());
const size = box.getSize(new THREE.Vector3());
const maxDim = Math.max(size.x, size.y, size.z);

// 1. Center the scene at origin (original scale)
gltf.scene.position.sub(center);

// 2. Add to pivot, then scale the PIVOT — not gltf.scene
pivot.add(gltf.scene);
pivot.scale.setScalar(2 / maxDim);
```

**Why**: if you scale `gltf.scene` after setting its position, the world-space center becomes `scale * center - center ≠ 0`. Scaling the parent pivot instead keeps the centering math intact.

### Interaction pattern
- Default: auto-rotate on Y axis (`pivot.rotation.y += 0.006` in animation loop)
- `mouseenter` canvas → pause auto-rotation, enable drag-to-spin (pointer events)
- `mouseleave` canvas → resume auto-rotation
- Use `canvas.setPointerCapture(e.pointerId)` on `pointerdown` so drag stays active when the cursor briefly leaves the canvas

### Component
`src/lib/components/TurtleComparison.svelte` — renders both models side by side with "FROM THIS → TO THIS" layout. Each model gets its own `Scene`, `Camera`, and `WebGLRenderer`.

### Three.js Memory Safety

Every Three.js component **must** follow these rules. Violations cause GPU memory leaks and accumulating RAF loops that degrade performance across navigations.

#### 1. Always cancel the animation loop on destroy

Store the RAF ID and cancel it in the `onMount` cleanup return:

```ts
let raf: number;
function tick() {
  raf = requestAnimationFrame(tick);
  renderer.render(scene, camera);
}
tick();

return () => {
  cancelAnimationFrame(raf); // required — never omit
};
```

`requestAnimationFrame` returns a numeric ID. Never call it as `requestAnimationFrame(tick)` without storing the return value.

#### 2. Use named functions for all event listeners

Anonymous arrow functions cannot be passed to `removeEventListener`. Always declare named functions:

```ts
// WRONG — cannot be removed later
canvas.addEventListener('pointerdown', (e) => { ... });

// CORRECT
function onPointerDown(e: PointerEvent) { ... }
canvas.addEventListener('pointerdown', onPointerDown);

return () => {
  canvas.removeEventListener('pointerdown', onPointerDown);
};
```

This applies to both `canvas.addEventListener` and `window.addEventListener`.

#### 3. Dispose GLTF scenes on destroy

Loaded GLTF models hold GPU-allocated geometries, materials, and textures. `renderer.dispose()` alone does **not** free them. Call this traversal on every loaded `gltf.scene` in the cleanup:

```ts
function disposeGltfScene(obj: THREE.Object3D) {
  obj.traverse((child: any) => {
    if (child.geometry) child.geometry.dispose();
    if (child.material) {
      const mats = Array.isArray(child.material) ? child.material : [child.material];
      for (const mat of mats) {
        for (const key of Object.keys(mat)) {
          const val = mat[key];
          if (val && val.isTexture) val.dispose();
        }
        mat.dispose();
      }
    }
  });
}

// In the loader callback, save a reference:
let loadedScene: THREE.Object3D | null = null;
loader.load(path, (gltf) => {
  loadedScene = gltf.scene;
  // ...
});

// In cleanup:
return () => {
  if (loadedScene) disposeGltfScene(loadedScene);
  renderer.dispose();
};
```

#### 4. Use persistent BufferGeometry for dynamic geometry

Never create a new `THREE.BufferGeometry` inside an animation loop — it allocates a new GPU buffer every frame and triggers constant GC. Allocate once, update in-place:

```ts
// WRONG — new allocation every frame
function tick() {
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(posArr, 3));
  scene.add(new THREE.LineSegments(geo, mat));
}

// CORRECT — allocate once, update in-place
const MAX_SEGS = 8000;
const posArr = new Float32Array(MAX_SEGS * 6); // 2 vertices × 3 floats per segment
const geo = new THREE.BufferGeometry();
const posAttr = new THREE.BufferAttribute(posArr, 3);
posAttr.setUsage(THREE.DynamicDrawUsage);
geo.setAttribute('position', posAttr);
scene.add(new THREE.LineSegments(geo, mat));

function tick() {
  let idx = 0;
  // ... fill posArr[idx++] = x; posArr[idx++] = y; posArr[idx++] = z; ...
  geo.setDrawRange(0, idx / 3); // number of vertices to draw (not byte count)
  posAttr.needsUpdate = true;   // signals Three.js to re-upload the buffer to GPU
}
```

#### 5. Full cleanup checklist

```ts
return () => {
  cancelAnimationFrame(raf);                            // 1. stop the loop
  window.removeEventListener('resize', onResize);       // 2. remove window listeners
  canvas.removeEventListener('pointerdown', onPointerDown); // 3. remove canvas listeners
  mixer?.stopAllAction();                               // 4. if AnimationMixer is used
  if (loadedScene) disposeGltfScene(loadedScene);       // 5. if a GLTF was loaded
  geo.dispose();                                        // 6. manually created geometries
  mat.dispose();                                        // 7. manually created materials
  renderer.dispose();                                   // 8. always last
};
```
