# Background.svelte — Math Deep Dive

This document explains every mathematical operation in `src/lib/components/Background.svelte`. Prerequisites: read `01-threejs-core.md` first.

The background renders a field of points connected by proximity-based line segments. Each point drifts, is pulled back toward its home position by a spring, is repelled by the mouse cursor, and bounces off the screen boundary.

---

## 1. The Orthographic Camera

```ts
const camera = new THREE.OrthographicCamera(-w/2, w/2, h/2, -h/2, 0.1, 100);
camera.position.z = 10;
```

The six parameters define the **frustum** — the region of world space the camera can see:

| Parameter | Value | Meaning |
|-----------|-------|---------|
| left | `-w/2` | left edge of visible area in world units |
| right | `w/2` | right edge |
| top | `h/2` | top edge |
| bottom | `-h/2` | bottom edge |
| near | `0.1` | minimum Z depth |
| far | `100` | maximum Z depth |

Because the left/right span exactly equals the screen width in pixels, and top/bottom spans the height, **world unit = 1 CSS pixel**. A point at world position `(300, 200, 0)` appears at exactly pixel `(300 + w/2, h/2 - 200)` on screen.

The orthographic projection formula (from `01-threejs-core.md`):

```
ndc_x = 2 * (x - left) / (right - left) - 1
      = 2 * (x + w/2) / w - 1
      = 2x/w                              (since +w/2 - w/2 = 0, simplified)
```

This maps `x = -w/2 → ndc = -1` and `x = w/2 → ndc = +1`, which then maps to `screen_x = 0` and `screen_x = w`.

The camera sits at `z = 10`. All geometry is at `z = 0`. Since `0 < 10 < 100`, it's within the near-far range and visible.

---

## 2. Building the Jittered Grid

```ts
const CELL = 60;
const JITTER = 15;

const cols = Math.ceil(width / CELL) + 1;
const rows = Math.ceil(height / CELL) + 1;
const ox = -width / 2 - CELL / 2;
const oy = -height / 2 - CELL / 2;

for (let r = 0; r <= rows; r++) {
  for (let c = 0; c <= cols; c++) {
    const hx = ox + c * CELL + (Math.random() * 2 - 1) * JITTER;
    const hy = oy + r * CELL + (Math.random() * 2 - 1) * JITTER;
  }
}
```

### Grid node positions

A regular grid node at column `c`, row `r` has base position:

```
base_x = ox + c × CELL
base_y = oy + r × CELL
```

Where the offset `ox = -w/2 - CELL/2` shifts the grid so it starts one cell outside the left edge. This ensures points exist beyond the screen boundary — without this, the leftmost column of points would be at exactly x = -w/2 and the screen edge would have no points near it, creating a visible sparse region. The same logic applies to `oy` for the top edge.

### Jitter

Each node is displaced by a random offset:

```
jitter = (Math.random() * 2 - 1) * JITTER
```

`Math.random()` returns a value uniformly distributed in `[0, 1)`. The expression `(Math.random() * 2 - 1)` remaps it to `(-1, 1)`, then multiplying by `JITTER = 15` gives a uniform random offset in `(-15, 15)` pixels.

The result is a **jittered grid** — a grid where each node is randomly displaced within ±15px of its regular position. This avoids the mechanical look of a perfect grid while maintaining approximate uniform coverage of the screen (each node stays near its cell center).

### Initial velocity

```ts
vx: (Math.random() * 2 - 1) * SPEED,  // SPEED = 0.25
vy: (Math.random() * 2 - 1) * SPEED,
```

Each point starts with a random velocity in `(-0.25, 0.25)` pixels per frame. At 60 fps this is ±15 px/s — slow enough to feel like a gentle drift.

---

## 3. Point Physics: The Per-Frame Update

Each frame, every point's velocity and position are updated. The order of operations matters.

### 3.1 Spring Force (Hooke's Law)

```ts
p.vx += (p.hx - p.x) * SPRING;   // SPRING = 0.008
p.vy += (p.hy - p.y) * SPRING;
```

This implements **Hooke's Law**: the restoring force of a spring is proportional to displacement:

```
F = k × (home - current_position)
```

Where `k = SPRING = 0.008` is the spring constant. When the point is far from home, the force is large; at home, the force is zero.

Since we're treating the point as having unit mass (F = ma, m=1, so a=F), this acceleration is added directly to velocity:

```
Δv = k × displacement
```

This is the **acceleration** the spring imparts each frame. It pulls the point back toward its home position.

### 3.2 Mouse Repulsion

```ts
const dx = p.x - mouseX;
const dy = p.y - mouseY;
const distSq = dx * dx + dy * dy;

if (distSq < MOUSE_RADIUS * MOUSE_RADIUS && distSq > 0.01) {
  const dist = Math.sqrt(distSq);
  const force = (MOUSE_RADIUS - dist) / MOUSE_RADIUS;
  p.vx += (dx / dist) * force * MOUSE_STRENGTH;
  p.vy += (dy / dist) * force * MOUSE_STRENGTH;
}
```

Breaking this down:

**Step 1: Direction from mouse to point**

```
direction = (p - mouse) / |p - mouse| = (dx/dist, dy/dist)
```

This is a **unit vector** pointing away from the mouse toward the point. Dividing by `dist` normalizes it to length 1.

**Step 2: Falloff (linear cone)**

```
force_magnitude = (MOUSE_RADIUS - dist) / MOUSE_RADIUS
```

When `dist = 0`: force = 1 (maximum). When `dist = MOUSE_RADIUS`: force = 0. Linear interpolation — the repulsion fades linearly with distance. This is different from gravity's inverse-square law (`1/r²`); this creates a "soft cone" of repulsion that smoothly disappears at the edge.

**Step 3: Apply**

```
Δvx = (dx/dist) × force_magnitude × MOUSE_STRENGTH
Δvy = (dy/dist) × force_magnitude × MOUSE_STRENGTH
```

The `distSq > 0.01` guard prevents division by zero if the mouse is exactly on a point.

**Why check squared distance first?**

```ts
if (distSq < MOUSE_RADIUS * MOUSE_RADIUS)
```

`Math.sqrt` is expensive (requires iterative computation). By squaring the threshold and comparing against `distSq = dx² + dy²`, we avoid calling `sqrt` for points outside the radius — the vast majority of points. The `sqrt` is only called when the point is close enough that we actually need the real distance.

**Mouse coordinate conversion:**

```ts
mouseX = e.clientX - w / 2;
mouseY = -(e.clientY - h / 2);
```

`clientX/Y` is in screen coordinates (0,0 at top-left, Y increases downward). The world coordinate system has (0,0) at center and Y increases upward. The conversion:
- Subtract `w/2`, `h/2` to move origin to screen center
- Negate Y to flip the axis

### 3.3 Damping

```ts
p.vx *= DAMPING;   // DAMPING = 0.90
p.vy *= DAMPING;
```

Each frame, velocity is multiplied by 0.90. This simulates **viscous drag** — friction proportional to velocity. Without damping, points would oscillate forever (the spring would impart equal and opposite velocity on each side of home). Damping removes energy from the system each frame.

**Why it works — geometric series:**

After N frames without any driving force, a velocity `v₀` becomes:

```
v_N = v₀ × 0.90^N
```

This is a geometric series that converges to 0. After 10 frames: `0.90^10 ≈ 0.35`. After 50 frames: `0.90^50 ≈ 0.005`. The point rapidly loses most velocity while still retaining enough to drift smoothly.

The spring constantly re-injects energy (pulling the point toward home), and damping removes it. The system reaches equilibrium where energy input = energy output, resulting in slow oscillation around the home position — the gentle drifting effect you see.

### 3.4 Position Integration

```ts
p.x += p.vx;
p.y += p.vy;
```

This is **Euler integration** (also called forward Euler): the simplest numerical method for solving differential equations. At each timestep, position is advanced by velocity × timestep. Since the timestep is one frame (≈1/60 s), and velocity is already in units of pixels-per-frame, the multiplication by timestep is implicit (1 frame × pixels/frame = pixels).

The full physics model in differential equation terms:

```
dv/dt = k(home - x) - γv + F_mouse
dx/dt = v
```

Where `k = SPRING = 0.008`, `γ = 1 - DAMPING = 0.10` (damping coefficient). Euler integration approximates this by:

```
v(t+Δt) = v(t) + Δt × (k(home - x) + F_mouse)  →  then multiply by DAMPING
x(t+Δt) = x(t) + Δt × v(t+Δt)
```

Note that damping is applied *before* position update (after adding forces). This is **semi-implicit Euler** (also called symplectic Euler), which is more energy-stable than pure forward Euler for oscillatory systems.

### 3.5 Boundary Bounce

```ts
const margin = 10;
const bx = w / 2 + margin;
const by = h / 2 + margin;

if (p.x < -bx) { p.x = -bx; p.vx = Math.abs(p.vx); }
if (p.x > bx)  { p.x = bx;  p.vx = -Math.abs(p.vx); }
if (p.y < -by) { p.y = -by; p.vy = Math.abs(p.vy); }
if (p.y > by)  { p.y = by;  p.vy = -Math.abs(p.vy); }
```

If a point crosses the boundary (screen edge + 10px margin):
1. Its position is clamped to the boundary
2. Its velocity component perpendicular to the wall is reflected to point inward

`Math.abs(p.vx)` ensures the velocity is always positive (away from the left wall), regardless of what it was. Similarly `-Math.abs(p.vx)` ensures it points left (away from the right wall). This is an **elastic reflection** — no energy is lost at the wall.

The `+10` margin allows points to drift slightly off-screen, making the effect appear to extend beyond the visible area.

---

## 4. The Proximity Graph: Connecting Points with Lines

```ts
function buildLines() {
  const positions: number[] = [];
  const n = points.length;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const dx = points[i].x - points[j].x;
      const dy = points[i].y - points[j].y;
      if (dx * dx + dy * dy < CONNECT_DIST * CONNECT_DIST) {
        positions.push(points[i].x, points[i].y, 0);
        positions.push(points[j].x, points[j].y, 0);
      }
    }
  }
  return new Float32Array(positions);
}
```

### Why O(n²)?

Every pair of points must be tested. With `n` points, there are `n(n-1)/2` pairs. This is O(n²) — if you double the number of points, you quadruple the work.

For a 1920×1080 screen with `CELL=60`: `cols ≈ 33`, `rows ≈ 19` → roughly 627 points → ~196,000 pairs to test each frame. This is fast enough because each test is just arithmetic (no branching other than the comparison).

Alternatives exist (k-d trees, spatial hashing) but add complexity. For ~600 points, the brute force approach is fine.

### The Squared Distance Trick

```
Euclidean distance: dist = √(dx² + dy²)
Comparison: dist < CONNECT_DIST
Equivalent: dx² + dy² < CONNECT_DIST²
```

`Math.sqrt` is significantly slower than multiplication. By squaring both sides of the inequality (valid since both sides are non-negative), we eliminate the square root entirely. `CONNECT_DIST * CONNECT_DIST = 105² = 11025` is computed once (or by the optimizer as a constant).

### `j = i + 1` — Avoiding Duplicate Edges

The outer loop starts `j` from `i + 1`, not from 0. This is a standard trick to enumerate **unordered pairs** — each pair `(i, j)` is tested exactly once, avoiding:
- Self-connections (`i == j`)
- Duplicate connections (testing `(0, 3)` and `(3, 0)` would add two line segments for the same edge)

### Building LineSegments

`THREE.LineSegments` expects positions in pairs: vertices `[0, 1]` form the first segment, `[2, 3]` form the second, etc. So for each connected pair, we push 6 floats: `x_i, y_i, 0, x_j, y_j, 0`.

The geometry is **rebuilt from scratch every frame** because every point moves. The old geometry is disposed to free GPU memory:

```ts
if (lineObj) { scene.remove(lineObj); lineObj.geometry.dispose(); }
```

Calling `.dispose()` releases the underlying WebGL buffer. Without it, every frame would leak GPU memory until the browser crashes.

---

## 5. Why Rebuild Geometry Every Frame?

An alternative would be to keep a fixed set of vertices and update their positions in-place using `geometry.attributes.position.needsUpdate = true`. This would be more efficient but requires knowing the maximum number of line segments in advance, since you can't resize a GPU buffer after creation.

Because the number of connected pairs changes every frame (as points move in and out of `CONNECT_DIST` of each other), the buffer size changes. Creating a fresh buffer each frame is the simplest correct solution.

---

## 6. Summary of the Math Stack

```
1. Screen coordinates   (pixels, origin top-left, Y down)
        ↓ converted by: mouseX = clientX - w/2, mouseY = -(clientY - h/2)
2. World coordinates    (pixels, origin center, Y up)
        ↓ governed by: spring + repulsion + damping + Euler integration
3. Point positions      (updated each frame)
        ↓ tested by: dx²+dy² < CONNECT_DIST² (O(n²) pairs)
4. Connected pairs      → Float32Array of vertex pairs
        ↓ uploaded to GPU as BufferGeometry
5. LineSegments         → rendered by orthographic camera
        ↓ projection: world pixel coords → screen pixel coords (1:1 mapping)
6. Screen pixels        (the final rendered lines you see)
```
