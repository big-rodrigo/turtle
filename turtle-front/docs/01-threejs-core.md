# How Three.js Works: Core Concepts and Math

This document explains the foundational machinery that every Three.js scene depends on. The other documents in this series build on top of everything explained here.

---

## 1. The Problem Three.js Solves

Your monitor is a flat grid of pixels. A 3D scene is a set of points in three-dimensional space. The job of a 3D renderer is to answer, for every pixel on screen: *what colour should this pixel be, given a 3D world and a camera?*

This involves two completely separate problems:

1. **Geometry**: where does each 3D point land on the 2D screen?
2. **Shading**: given that a point is visible, what colour is it (based on its material, lights, and angle)?

Three.js is a JavaScript library that abstracts the low-level GPU API (WebGL) and lets you think in terms of scenes, cameras, meshes, and materials instead of raw byte buffers and shader programs.

---

## 2. WebGL and the GPU Pipeline

Three.js renders through **WebGL**, which is a JavaScript binding to OpenGL ES — a standardised interface for talking to your computer's GPU.

The GPU cannot run arbitrary code the way a CPU can. Instead, it runs **shaders** — small programs that execute in parallel on thousands of cores at once. The pipeline has two main stages:

### 2.1 Vertex Shader

Runs **once per vertex** (corner of a triangle). Its only job is to compute where that vertex lands on screen. The output is a position in **clip space** (explained in section 6).

Three.js writes the vertex shader for you. When you set `mesh.position`, `mesh.rotation`, `mesh.scale`, Three.js encodes those transforms into a matrix called the **model matrix** and passes it to the vertex shader as a `uniform` (a value shared across all vertices of that draw call).

### 2.2 Rasterization

The GPU takes the triangles (defined by three clip-space vertices each) and fills in all the pixels that fall inside them. For each interior pixel it interpolates data (like UV coordinates or normals) from the three corners. This is purely hardware — you write no code for this step.

### 2.3 Fragment Shader

Runs **once per rasterized pixel** (also called a fragment). It determines the final colour of that pixel. This is where lighting calculations happen — it receives the interpolated normal vector and UV coordinates, samples textures, evaluates the lighting model, and outputs an RGBA colour.

Three.js generates the fragment shader from your material settings (`MeshStandardMaterial`, `MeshBasicMaterial`, etc).

### Summary diagram

```
CPU (JavaScript)
  │
  ├─ Upload geometry (vertices, normals, UVs) to GPU buffers
  ├─ Set uniforms (model matrix, light positions, material properties)
  │
GPU
  ├─ [Vertex Shader] × N_vertices   →  clip-space positions
  ├─ [Rasterization]                →  pixel fragments with interpolated data
  └─ [Fragment Shader] × N_pixels   →  final pixel colours
```

---

## 3. The Coordinate System

Three.js uses a **right-handed, Y-up** coordinate system:

```
        +Y (up)
         │
         │
         └──────── +X (right)
        /
       /
     +Z (toward you, out of the screen)
```

"Right-handed" means: if you point your right hand's fingers along +X and curl them toward +Y, your thumb points along +Z. This is the standard convention in mathematics and physics; OpenGL uses it too.

**Practical consequences:**
- The camera by default looks down the **-Z axis** (into the screen)
- Positive rotations on Y rotate counter-clockwise when viewed from above
- `position.set(1, 0, 0)` moves an object to the right

---

## 4. Homogeneous Coordinates and the 4×4 Matrix

You might expect 3D transforms to use 3×3 matrices. They don't — and here is why.

A 3×3 matrix can represent rotation and scale but **not translation**. Translation requires adding a vector, which is a different kind of operation. To unify all three transforms into a single matrix multiplication, computer graphics uses **homogeneous coordinates**: every 3D point `(x, y, z)` is represented as a 4D vector `(x, y, z, 1)`.

With 4D vectors, a 4×4 matrix can encode translation:

```
Translation by (tx, ty, tz):

    ┌ 1  0  0  tx ┐   ┌ x ┐   ┌ x + tx ┐
    │ 0  1  0  ty │ × │ y │ = │ y + ty │
    │ 0  0  1  tz │   │ z │   │ z + tz │
    └ 0  0  0   1 ┘   └ 1 ┘   └   1   ┘
```

Scale by `(sx, sy, sz)`:

```
    ┌ sx  0   0   0 ┐
    │  0  sy  0   0 │
    │  0   0  sz  0 │
    └  0   0   0  1 ┘
```

Rotation around Y by angle θ:

```
    ┌  cos θ  0  sin θ  0 ┐
    │    0    1    0    0 │
    │ -sin θ  0  cos θ  0 │
    └    0    0    0    1 ┘
```

Rotation around X by angle θ:

```
    ┌ 1    0       0    0 ┐
    │ 0  cos θ  -sin θ  0 │
    │ 0  sin θ   cos θ  0 │
    └ 0    0       0    1 ┘
```

Rotation around Z by angle θ:

```
    ┌ cos θ  -sin θ  0  0 ┐
    │ sin θ   cos θ  0  0 │
    │   0       0    1  0 │
    └   0       0    0  1 ┘
```

The key insight: **matrix multiplication is associative**, so you can pre-multiply any combination of T, R, and S into a single 4×4 matrix M. Then, transforming any point only requires one matrix-vector multiplication.

---

## 5. The Model Matrix: T · R · S

When you write:

```ts
mesh.position.set(1, 0, 0);   // translation
mesh.rotation.y = Math.PI/4;  // rotation 45° around Y
mesh.scale.set(2, 1, 1);      // non-uniform scale
```

Three.js builds the **model matrix** M as:

```
M = T · R · S
```

**Order matters** because matrix multiplication is not commutative (A·B ≠ B·A in general).

The Three.js convention is **Scale first, then Rotate, then Translate**:

1. Scale stretches the object around its local origin
2. Rotation rotates the already-scaled object around its local origin
3. Translation moves it to its final world position

This is the intuitive order: you resize the object, orient it, then place it. If you translated first, rotation would swing the object around the world origin instead of its own center.

**Example**: a leg at `pos=(-0.8, -0.05, 0.5)` rotated `rz=0.35π`:

1. Scale (default 1,1,1 — no change)
2. Rotate 63° around Z — tips the capsule to the side
3. Translate — moves it to the left-front position on the turtle's body

If those steps were reversed (translate then rotate), the rotation would spin the leg around the turtle's origin at (0,0,0) instead of the leg's own center.

---

## 6. The Camera Pipeline: From Local Space to Screen

A vertex travels through four coordinate spaces before becoming a pixel:

```
Local Space  →[M]→  World Space  →[V]→  Camera Space  →[P]→  Clip Space  →[÷w]→  NDC  →[viewport]→  Screen
```

### 6.1 Local Space → World Space (Model Matrix M)

Each mesh has its own local coordinate system centered at (0,0,0). The model matrix M transforms from that local space into the shared world space.

### 6.2 World Space → Camera Space (View Matrix V)

The camera is also an object in the world with a position and orientation. To transform into **camera space** (also called eye space), you apply the inverse of the camera's own world transform:

```
V = inverse(camera_world_matrix)
```

In camera space, the camera is always at the origin looking down -Z. Every other object is repositioned relative to the camera.

`camera.lookAt(0, 0, 0)` computes the camera's orientation matrix from three vectors: eye position, target point, and up vector. The view matrix is derived from this.

### 6.3 Camera Space → Clip Space (Projection Matrix P)

The projection matrix encodes the camera's "lens" — how the 3D volume visible to the camera maps to the 2D screen. There are two types:

#### Perspective Projection

Objects farther away appear smaller. This is the natural way human eyes and cameras work. The math:

```
  f = 1 / tan(fov/2)          where fov is the vertical field of view in radians

  P_perspective =
    ┌ f/aspect   0        0                    0               ┐
    │    0       f        0                    0               │
    │    0       0   -(far+near)/(far-near)  -2·far·near/(far-near) │
    └    0       0       -1                    0               ┘
```

The `-1` in row 3, column 4 places the camera-space Z into the homogeneous W component. After dividing by W (the perspective divide), objects farther away get smaller X and Y values — that's the perspective effect.

For the Turtle3D component: `PerspectiveCamera(45, 1, 0.1, 100)`
- `fov = 45°` → `f = 1/tan(22.5°) ≈ 2.414`
- `aspect = 1` (280×280 square canvas)
- `near = 0.1`, `far = 100`

#### Orthographic Projection

All rays are parallel — there is no perspective shrinking. Used in the Background component to map world units directly to pixels:

```
  P_orthographic =
    ┌ 2/(r-l)     0       0      -(r+l)/(r-l) ┐
    │    0     2/(t-b)    0      -(t+b)/(t-b) │
    │    0        0    -2/(f-n)  -(f+n)/(f-n) │
    └    0        0       0           1        ┘
```

Where l=left, r=right, t=top, b=bottom, n=near, f=far.

For Background: `OrthographicCamera(-w/2, w/2, h/2, -h/2, 0.1, 100)` maps the rectangle `[-w/2 .. w/2] × [-h/2 .. h/2]` exactly to the canvas, so a point at world position `(px, py)` (in pixels) maps directly to that pixel.

### 6.4 Clip Space → NDC → Screen

After applying P, positions are in **clip space**. The GPU then performs the **perspective divide**: divides x, y, z by w, yielding **Normalized Device Coordinates (NDC)** in the range `[-1, 1]³`.

Finally the **viewport transform** maps NDC to pixel coordinates:

```
  screen_x = (ndc_x + 1) / 2 × width
  screen_y = (1 - ndc_y) / 2 × height    ← Y flipped because screen Y goes down
```

### 6.5 The Combined Transform

The vertex shader computes:

```
clip_position = P · V · M · local_position
```

Three.js computes `P·V` on the CPU as `projectionMatrix × matrixWorldInverse` and passes it to the shader as `projectionViewMatrix`. The model matrix M is passed separately as `modelMatrix`.

---

## 7. Euler Angles

Three.js represents rotations using **Euler angles**: three angles (X, Y, Z) applied in sequence. When you write `mesh.rotation.y = angle`, you're setting the Y Euler angle.

The default order in Three.js is **XYZ**, meaning the rotation matrix is computed as:

```
R = Rz · Ry · Rx
```

(Applied right-to-left: first X, then Y, then Z.)

**Gimbal lock** is a limitation of Euler angles: if the Y rotation reaches ±90°, the X and Z axes align, losing one degree of freedom. For the animations in this project (slow continuous Y rotation), this is never a problem. GLTF files store rotations as quaternions to avoid it entirely — see `04-gltf-models.md`.

---

## 8. The Scene Graph

Three.js organizes objects in a tree called the **scene graph**. Every node (`Object3D`) has:
- Its own local transform (position, rotation, scale)
- A list of children, each with their own transforms

When Three.js computes an object's **world matrix**, it multiplies all transforms from root to leaf:

```
world_matrix = parent.world_matrix × child.local_matrix
```

A `Group` is an `Object3D` with no geometry of its own — just a container. When you rotate a `Group`, all its children rotate together in the group's local space. This is used in Turtle3D to rotate the entire turtle by rotating `turtleGroup.rotation.y`.

The key property: **children's local coordinates are relative to their parent, not the world**. So a leg at `position (0.8, 0, 0)` in the turtle group's local space is 0.8 units to the right of the *turtle's center*, not the world origin. When the turtle group rotates, the leg rotates with it as if glued.

---

## 9. BufferGeometry and GPU Memory

Three.js stores geometry in `BufferGeometry` objects. A geometry is a collection of **attributes** — named arrays of numbers uploaded to the GPU.

The most important attribute is `position`: for each vertex, three floats `[x, y, z]`. A triangle with 3 vertices occupies 9 floats. Three.js creates a `Float32Array` (32-bit IEEE 754 floats) because GPUs operate natively on 32-bit floats.

```ts
const geo = new THREE.BufferGeometry();
const positions = new Float32Array([
  x0, y0, z0,   // vertex 0
  x1, y1, z1,   // vertex 1
  x2, y2, z2,   // vertex 2
]);
geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
//                                                               ^ itemSize (floats per vertex)
```

The `3` is the **item size** — how many floats constitute one vertex's position. For `LineSegments`, every two consecutive vertices form one line segment, so positions come in pairs.

Other standard attributes:
- `normal` (3 floats/vertex): direction perpendicular to the surface at that point — used by lighting calculations
- `uv` (2 floats/vertex): texture coordinate — which point on the texture image maps to this vertex

---

## 10. The Render Loop: requestAnimationFrame

```ts
function tick() {
  raf = requestAnimationFrame(tick);
  // update scene...
  renderer.render(scene, camera);
}
tick();
```

`requestAnimationFrame` asks the browser to call your function before the next repaint, synchronized to the display refresh rate (typically 60 Hz, sometimes 120 Hz). It does not guarantee exactly 60 calls per second — it stops when the tab is hidden (saving CPU/GPU), and the actual interval depends on system load.

The implication: rotation increments like `turtleGroup.rotation.y += 0.008` are **frame-rate dependent**. At 60 fps: `0.008 rad/frame × 60 frame/s = 0.48 rad/s ≈ 27.5°/s`. At 120 fps the turtle would spin twice as fast. The correct approach (not used here for simplicity) is to multiply increments by `deltaTime` (elapsed milliseconds since last frame).

---

## 11. The Renderer

`THREE.WebGLRenderer` manages the WebGL context and the render cycle:

```ts
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(width, height);
```

- **`antialias: true`**: enables MSAA (Multi-Sample Anti-Aliasing) — samples each pixel multiple times at sub-pixel offsets and averages, smoothing jagged triangle edges. Costs GPU memory and fill rate.
- **`alpha: true`**: makes the WebGL context support transparency, so the canvas background can be transparent (showing the HTML background behind it).
- **`devicePixelRatio`**: on HiDPI/Retina screens, one CSS pixel corresponds to 2 or more physical pixels. Setting the pixel ratio to 2 makes the renderer draw at full physical resolution. Capped at 2 to avoid extreme overdraw on 3× or 4× density screens.
- **`renderer.setSize(w, h)`**: sets both the canvas drawing buffer size and the CSS size.

When `renderer.render(scene, camera)` is called, Three.js:
1. Traverses the scene graph, computing world matrices for all objects
2. Culls objects outside the camera frustum
3. Sorts transparent objects back-to-front
4. Issues WebGL draw calls for each visible mesh
