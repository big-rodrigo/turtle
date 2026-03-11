# TurtleComparison.svelte — Math Deep Dive

This document explains every mathematical operation in `src/lib/components/TurtleComparison.svelte`. This component loads two real 3D models from GLTF files and renders them with interactive controls. Prerequisites: read `01-threejs-core.md` first.

---

## 1. What is GLTF?

**GLTF** (GL Transmission Format, pronounced "gift") is an open standard for 3D model files, designed to be efficient for GPU delivery. It's the "JPEG of 3D" — compact and widely supported.

A GLTF package consists of:

```
scene.gltf   — JSON file: scene graph, mesh descriptions, material properties, texture references
scene.bin    — Binary blob: raw vertex data (positions, normals, UVs) and animation data
textures/    — Image files (PNG/JPEG) for albedo, normal maps, roughness maps, etc.
```

### 1.1 The GLTF JSON Structure

```json
{
  "scenes": [{ "nodes": [0] }],
  "nodes": [
    { "name": "RootNode", "children": [1, 2] },
    { "name": "Body", "mesh": 0, "translation": [0, 0.5, 0] },
    { "name": "Shell", "mesh": 1, "rotation": [0, 0, 0, 1] }
  ],
  "meshes": [...],
  "accessors": [...],
  "bufferViews": [...],
  "buffers": [{ "uri": "scene.bin", "byteLength": 92344 }]
}
```

The **node hierarchy** is a tree — exactly like Three.js's scene graph. Each node can have a **TRS** (Translation, Rotation, Scale):

- `translation`: `[x, y, z]` in meters/world units
- `rotation`: `[x, y, z, w]` — a **quaternion** (explained below)
- `scale`: `[sx, sy, sz]`

### 1.2 How Vertex Data is Stored

The `.bin` file is a raw byte buffer. The GLTF JSON describes how to interpret it using:

- **Accessor**: describes a typed array within the buffer (e.g., "starting at byte 0, 1024 VEC3 FLOAT values")
- **BufferView**: a window into the binary buffer (byte offset + byte length)

Example: to read positions for 512 vertices:
```
accessor.byteOffset = 0
accessor.count = 512
accessor.componentType = FLOAT (5126 = 32-bit IEEE 754)
accessor.type = VEC3 (3 components per element)
Total bytes = 512 × 3 × 4 = 6144 bytes
```

Three.js's `GLTFLoader` reads these descriptors and creates `THREE.BufferGeometry` objects with the correct `Float32Array` data — no manual parsing needed.

---

## 2. Quaternions

GLTF stores rotations as **quaternions** instead of Euler angles. This is important to understand because Squirt's root joint has a quaternion `[-0.707, 0, 0, 0.707]` which produces a 90° Y rotation, causing the model dislocation we had to fix.

### 2.1 What is a Quaternion?

A quaternion `q = (x, y, z, w)` encodes a rotation as:

```
q = (sin(θ/2) × axis.x,
     sin(θ/2) × axis.y,
     sin(θ/2) × axis.z,
     cos(θ/2))
```

Where `axis` is the unit vector around which the rotation occurs, and `θ` is the angle.

For Squirt's hip joint rotation `q ≈ (0.0037, -0.7071, 0.0037, 0.7071)`:

```
w = cos(θ/2) = 0.7071 ≈ cos(π/4) → θ/2 = π/4 → θ = π/2 = 90°

axis = (sin(θ/2)×x, sin(θ/2)×y, sin(θ/2)×z) / |...|
     ≈ (0.0037, -0.7071, 0.0037) / 0.7071
     ≈ (0.005, -1.000, 0.005)   ≈ (0, -1, 0) = -Y axis
```

So this joint is rotated **90° around -Y** — a quarter turn. Combined with the `translation: [0, 0.764, -0.068]` offset, the root of Squirt's mesh is placed 0.764 units up and 0.068 units back, *then* rotated 90° — explaining why the model appears rotated and offset when loaded without centering.

### 2.2 Why Quaternions?

**Euler angles** (`rotation.x, rotation.y, rotation.z`) have a fatal problem called **gimbal lock**: when the Y rotation reaches ±90°, the X and Z axes align and you lose one degree of freedom. The object becomes impossible to rotate in certain directions.

Quaternions avoid this because they represent rotation in 4D space, which has no equivalent singularity. They also **interpolate smoothly** (using SLERP — Spherical Linear Interpolation), which is critical for animations.

### 2.3 Converting Quaternion to Rotation Matrix

Three.js converts GLTF quaternions to 4×4 matrices internally:

```
R = ┌ 1-2(y²+z²)   2(xy-wz)    2(xz+wy)  0 ┐
    │  2(xy+wz)   1-2(x²+z²)   2(yz-wx)  0 │
    │  2(xz-wy)    2(yz+wx)   1-2(x²+y²) 0 │
    └     0           0           0       1 ┘
```

For the unit quaternion `q = (0, -0.7071, 0, 0.7071)` (90° around -Y):

```
x=0, y=-0.7071, z=0, w=0.7071:

1 - 2(y²+z²) = 1 - 2(0.5+0) = 0
2(xy-wz) = 0
2(xz+wy) = 2(0 + 0.7071×(-0.7071)) = 2(-0.5) = -1
→ row 0: [0, 0, -1, 0]

2(xy+wz) = 0
1 - 2(x²+z²) = 1
2(yz-wx) = 0
→ row 1: [0, 1, 0, 0]

2(xz-wy) = 2(0 - (-0.7071×0.7071)) = 2(0.5) = 1
2(yz+wx) = 0
1 - 2(x²+y²) = 1 - 2(0+0.5) = 0
→ row 2: [1, 0, 0, 0]
```

Result:
```
R = ┌  0   0  -1  0 ┐
    │  0   1   0  0 │
    │  1   0   0  0 │
    └  0   0   0  1 ┘
```

This maps `+X → +Z` and `+Z → -X` — a 90° rotation around -Y, exactly as derived from the quaternion.

---

## 3. The GLTFLoader

```ts
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

const loader = new GLTFLoader();
loader.load(modelPath, (gltf) => {
  // gltf.scene is a THREE.Group containing the full hierarchy
});
```

`GLTFLoader.load` is **asynchronous** — it fires an HTTP request for the `.gltf` file, parses the JSON, then fires additional requests for `.bin` and textures. The callback fires only after all assets are loaded. The scene continues rendering during loading — the pivot exists in the scene from frame 1, but it's empty until the callback fires.

Internally, the loader:
1. Parses `scene.gltf` JSON
2. Fetches `scene.bin` as an `ArrayBuffer`
3. For each mesh in each node, creates a `THREE.BufferGeometry`:
   - Reads positions from the bin buffer (using accessor offsets)
   - Reads normals, UVs, and other attributes
   - Creates `THREE.MeshStandardMaterial` from GLTF PBR material properties
4. Assembles nodes into a `THREE.Group` hierarchy matching the GLTF node tree
5. Fires the callback with the assembled `gltf` object

---

## 4. Axis-Aligned Bounding Box (AABB)

```ts
const box = new THREE.Box3().setFromObject(gltf.scene);
const center = box.getCenter(new THREE.Vector3());
const size = box.getSize(new THREE.Vector3());
const maxDim = Math.max(size.x, size.y, size.z);
```

### 4.1 What is a Bounding Box?

An **Axis-Aligned Bounding Box** is the smallest rectangular box, aligned with the coordinate axes, that contains all vertices of the object. It's defined by two corners: `min = (min_x, min_y, min_z)` and `max = (max_x, max_y, max_z)`.

"Axis-aligned" means the box edges are parallel to the X, Y, and Z axes. This makes computation fast (just find the min/max of each coordinate independently) but the box may be larger than necessary for rotated objects.

### 4.2 setFromObject — Traversing the Scene Graph

`Box3.setFromObject(gltf.scene)` traverses the entire scene graph (all children recursively) and for each `Mesh`, transforms its vertex positions into world space and finds the overall min/max.

For each vertex `v` of a mesh:
```
world_v = mesh.matrixWorld × v
```

Then:
```
box.min.x = min(box.min.x, world_v.x)
box.min.y = min(box.min.y, world_v.y)
...etc
```

This accounts for all the GLTF node transforms (translation, rotation, scale) that were applied during loading, including Squirt's offset root joint.

### 4.3 Center and Size

```
center = (min + max) / 2         (midpoint formula)
size   = max - min                (component-wise subtraction)

center.x = (min_x + max_x) / 2
center.y = (min_y + max_y) / 2
center.z = (min_z + max_z) / 2

size.x = max_x - min_x
size.y = max_y - min_y
size.z = max_z - min_z
```

`maxDim = max(size.x, size.y, size.z)` is the length of the longest axis of the bounding box — used to normalize the model to a standard size.

---

## 5. The Centering Fix: Why Pivot Scaling Works

```ts
gltf.scene.position.sub(center);
pivot.add(gltf.scene);
pivot.scale.setScalar(2 / maxDim);
```

This is the fix for the Squirt dislocation. Let's derive exactly why it works.

### 5.1 The World Position Formula

For a vertex in `gltf.scene` with local position `v_local`, the world position is:

```
v_world = pivot.position + pivot.rotation × (pivot.scale × (
              gltf.scene.position + gltf.scene.rotation × (gltf.scene.scale × v_local)
          ))
```

Since the pivot starts at the origin with identity rotation:
```
v_world = pivot.scale × (gltf.scene.position + gltf.scene.rotation × (gltf.scene.scale × v_local))
```

Let `S_pivot = 2/maxDim` (the pivot scale scalar), `p_scene = gltf.scene.position` (the position we set), and `R_scene, S_scene` be the gltf.scene rotation and scale (which we don't change):

```
v_world = S_pivot × (p_scene + R_scene × (S_scene × v_local))
```

### 5.2 What `gltf.scene.position.sub(center)` Does

Before this operation, `gltf.scene.position` is whatever the GLTF file says (often `[0,0,0]` at the scene root level). `Box3.setFromObject` computed `center` as the world-space centroid of all vertices.

`position.sub(center)` sets:
```
p_scene = original_position - center ≈ (0,0,0) - center = -center
```

So:
```
v_world = S_pivot × (-center + R_scene × (S_scene × v_local))
        = S_pivot × (v_in_gltf_scene_space - center)
```

Where `v_in_gltf_scene_space = R_scene × (S_scene × v_local)` is the vertex after the GLTF internal transforms.

The centroid of all `v_in_gltf_scene_space` values is exactly `center` (by definition — that's what Box3 computed). So:

```
centroid of all v_world = S_pivot × (center - center) = S_pivot × 0 = 0
```

**The model is centered at the world origin, regardless of S_pivot.**

### 5.3 Why the Buggy Version Failed

The original (buggy) code:
```ts
gltf.scene.position.sub(center);    // p_scene = -center
gltf.scene.scale.setScalar(scale);  // now gltf.scene.scale = scale
pivot.add(gltf.scene);
```

Here, both position and scale are on `gltf.scene`. The world position formula (with pivot at origin, scale=1):

```
v_world = p_scene + R_scene × (S_scene_new × v_local)
        = -center + R_scene × (scale × v_local)
        = -center + scale × (R_scene × v_local)
```

The centroid of all `R_scene × v_local` before scaling was `center` (that's what Box3 found, since the original gltf.scene had no explicit position). After scaling: `scale × center`. So:

```
centroid of v_world = -center + scale × center = center × (scale - 1)
```

For `scale ≠ 1`, this is non-zero. The model is off-center by `center × (scale - 1)`. For Squirt, `center ≈ (0, 0.38, -0.03)` and a scale of roughly 2 (to fit into the view), the offset would be `(0, 0.38, -0.03)` — about 0.38 units up. That's the dislocation visible in the screenshot.

### 5.4 Scale to Fit: 2 / maxDim

`pivot.scale.setScalar(2 / maxDim)` makes the largest dimension of the model exactly 2 world units. Since the camera is at `z = 3` and the model is centered at the origin, the model fits within roughly `[-1, 1]` in all dimensions — comfortably within the camera's view.

The factor `2` was chosen empirically to fill the canvas without clipping. The FOV of 45° at distance 3 gives a visible half-height of `3 × tan(22.5°) ≈ 1.24` — so a model of ±1 unit occupies about `1/1.24 ≈ 81%` of the canvas height.

---

## 6. Lighting in TurtleComparison

The lighting setup uses **DirectionalLight** instead of PointLight.

```ts
const key = new THREE.DirectionalLight(0xe8edfc, 1.4);
key.position.set(2, 3, 4);
scene.add(key);
```

### DirectionalLight vs PointLight

| Property | DirectionalLight | PointLight |
|----------|-----------------|------------|
| Rays | All parallel, same direction | Radiate from a point in all directions |
| Attenuation | None — same intensity everywhere | Inverse-square (or custom) |
| Models | The sun | A bare light bulb |
| `position` meaning | Direction of light (defines which way rays travel) | Location in world space |

For a `DirectionalLight` at position `(2, 3, 4)`, the light direction is `normalize(2, 3, 4) → (0.371, 0.557, 0.743)`. Every fragment in the scene receives light from exactly this direction, regardless of its world position.

The lighting computation for a directional light:

```
L = normalize(light.position)      // direction toward light (same for all fragments)
N = surface normal (varies per fragment)
diffuse = max(0, dot(N, L)) × light.colour × light.intensity
```

This is **Lambert's cosine law**: a surface directly facing the light (`dot(N,L) = 1`) receives full illumination; one at 90° (`dot(N,L) = 0`) receives none; one facing away gets `max(0, ...)` = 0 (no negative illumination).

### The Lighting Rig

```ts
ambient = AmbientLight(0x1a3ecf, 0.5)         // base blue fill
key     = DirectionalLight(0xe8edfc, 1.4)  pos(2,3,4)    // main white light, upper-right-front
rim     = DirectionalLight(0x1a3ecf, 0.8)  pos(-3,1,-3)  // blue rim, left-rear
fill    = DirectionalLight(0x4060ff, 0.3)  pos(0,-2,2)   // subtle blue underlight
```

This is a classic **three-point lighting** setup adapted for cyberpunk:
- **Key**: strong white light from upper-right-front — reveals main form and detail
- **Rim**: blue light from left-rear — outlines the silhouette against the dark background
- **Fill**: low-intensity blue from below — softens shadows, prevents areas from going completely black
- **Ambient**: constant blue base — ties everything together with the colour palette

---

## 7. PBR Textures in GLTF

The Giant Armored Turtle monster has three texture maps, which the `GLTFLoader` applies automatically to a `MeshStandardMaterial`.

### 7.1 baseColor (Albedo)

`textures/tripo_mat_087c6ce7_baseColor.jpeg`

The **albedo** is the "base colour" of the surface — what colour light it reflects diffusely. In PBR, albedo is a linear-space colour per texel. JPEG compression means slight colour inaccuracies in fine details, but acceptable for a game-quality asset.

In the fragment shader:
```
diffuse_colour = albedo_texture.sample(uv) × material.baseColorFactor
```

### 7.2 metallicRoughness (Packed Map)

`textures/tripo_mat_087c6ce7_metallicRoughness.png`

GLTF packs two PBR parameters into one texture to save memory:
- **Green channel (G)**: roughness — 0 = mirror smooth, 1 = completely rough
- **Blue channel (B)**: metalness — 0 = dielectric, 1 = metal
- Red channel is unused (often 0)

```
roughness = metallicRoughness_texture.g × material.roughnessFactor
metalness = metallicRoughness_texture.b × material.metallicFactor
```

This drives the GGX microfacet model (explained in `03-turtle3d.md`), varying roughness and metalness across the surface — the spikes on the armored turtle are likely sharper (lower roughness) than the skin between them.

### 7.3 Normal Map (Tangent-Space Normals)

`textures/tripo_mat_087c6ce7_normal.png`

A **normal map** stores a fake surface normal per texel, encoded as an RGB colour:

```
R → normal.x ∈ [-1, 1]   (decoded as: R/255 × 2 - 1)
G → normal.y ∈ [-1, 1]
B → normal.z ∈ [-1, 1]

Typically appears blue/purple because most normals point roughly toward (0, 0, 1) = RGB (128, 128, 255)
```

The normal is stored in **tangent space** — a local coordinate system for each surface point where:
- Z = the actual geometric normal
- X = the tangent (along U texture axis)
- Y = the bitangent (along V texture axis)

To use the normal map in lighting, the shader constructs the **TBN matrix** (Tangent, Bitangent, Normal) from vertex attributes:

```
TBN = mat3(tangent, bitangent, normal)      // 3×3 rotation matrix

sampled_normal = normalize(normal_texture.sample(uv) × 2 - 1)   // unpack [-1,1]
world_normal   = normalize(TBN × sampled_normal)                  // to world space
```

This lets flat triangles appear to have detailed bumps, dents, and grooves (like the armored turtle's spikes) without adding geometric complexity.

---

## 8. ACESFilmic Tonemapping

```ts
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.2;
```

### Why Tonemapping?

PBR rendering works in **HDR (High Dynamic Range)** — light values can exceed 1.0. A white LED might have an intensity value of 10 or 100. But monitors can only display values in `[0, 1]`. **Tonemapping** maps the HDR range to `[0, 1]` in a perceptually pleasing way.

### The ACES Curve

ACES (Academy Color Encoding System) is an industry-standard colour pipeline used in film production. The filmic tonemapping curve:

```
f(x) = x(2.51x + 0.03) / (x(2.43x + 0.59) + 0.14)
```

Applied per channel (R, G, B independently):

Key properties of this curve:
- `f(0) = 0`: black stays black
- `f(1) ≈ 0.805`: a value of 1 maps to about 80% brightness (slightly compressed)
- As `x → ∞`: `f(x) → 2.51/2.43 ≈ 1.033`, but clamped to 1
- The curve has an S-shape: **crushes deep shadows** (low x → very low f(x)), **compresses bright highlights** (high x → approaches 1 smoothly), **enhances midtones** (middle range has increased contrast)

This is why the GLTF models look more "cinematic" than the procedural turtle: their lighting is tonemapped through ACES, while `Turtle3D.svelte` uses the default linear tonemapping (no ACES).

### Exposure

`toneMappingExposure = 1.2` multiplies all HDR values by 1.2 before tonemapping:

```
final_colour = ACES(colour × exposure)
             = ACES(colour × 1.2)
```

This slightly brightens the scene — 1.0 would be "correct" exposure, 1.2 is one-fifth of a stop overexposed. It prevents the GLTF models from appearing too dark (a common issue with dark GLTF assets on dark backgrounds).

---

## 9. sRGB Colour Space

```ts
renderer.outputColorSpace = THREE.SRGBColorSpace;
```

### The Gamma Problem

Computer monitors don't display light linearly. A stored value of 0.5 doesn't produce half the brightness of 1.0 — it produces roughly `0.5^(1/2.2) ≈ 0.73` (about 73%). This is the **sRGB gamma curve**.

Textures are stored in sRGB space (gamma-encoded) because:
1. Human vision is logarithmic — we perceive more differences in dark tones
2. sRGB encoding allocates more bits to dark tones, matching perception
3. History: monitors were designed to display this encoding directly

**The linear workflow**: to get correct lighting math, you must:
1. Convert texture values from sRGB → linear when reading (gamma expand: `value^2.2`)
2. Do all lighting math in linear space
3. Convert final colour from linear → sRGB when writing to screen (gamma compress: `value^(1/2.2)`)

Three.js handles step 1 automatically when you set `texture.colorSpace = THREE.SRGBColorSpace` (which GLTFLoader does for colour textures). Setting `renderer.outputColorSpace = THREE.SRGBColorSpace` handles step 3 — the final gamma compression before the rendered image appears on screen.

Without this, the scene appears overly dark or colours appear washed out.

---

## 10. Mouse Drag: Pixel Delta to Rotation

```ts
function onPointerMove(e: PointerEvent) {
  if (!isDragging) return;
  const dx = e.clientX - lastX;
  const dy = e.clientY - lastY;
  pivot.rotation.y += dx * 0.01;
  pivot.rotation.x += dy * 0.01;
  lastX = e.clientX;
  lastY = e.clientY;
}
```

### Mapping Pixels to Radians

`dx` is the mouse movement in CSS pixels since the last frame. The sensitivity constant `0.01` converts pixels to radians:

```
Δrotation.y = dx_pixels × 0.01 rad/pixel
```

At `0.01 rad/px`:
- Moving 10 pixels → 0.1 rad ≈ 5.7°
- Moving 100 pixels → 1.0 rad ≈ 57°
- Moving 314 pixels → π rad = 180° (half rotation)

For a 320px-wide canvas, dragging all the way across rotates the model about 180°. This is the standard convention for model viewers.

### Why Y for Horizontal, X for Vertical?

When the user drags **horizontally** (dx), they expect the model to spin around a vertical axis — that's the Y axis.

When the user drags **vertically** (dy), they expect the model to tilt up/down — that's rotation around the horizontal (X) axis.

In screen coordinates, positive Y is downward. In Three.js, positive X rotation tilts the top of the object toward the camera. Dragging down (positive dy) should tilt the model's top toward you — positive X rotation. The signs work out naturally with `rotation.x += dy * 0.01`.

### setPointerCapture

```ts
canvas.setPointerCapture(e.pointerId);
```

When a pointer button is pressed, `setPointerCapture` binds all future pointer events from that pointer to the canvas element — even if the cursor moves outside the canvas bounds. Without this:
- User presses on canvas, starts dragging
- Cursor leaves canvas boundary
- `pointermove` events stop firing (they'd go to whatever element is under the cursor)
- Drag becomes jerky or stops

With `setPointerCapture`, the OS routes all events from that pointer ID to the canvas until `pointerup` fires. This is essential for any drag interaction.

---

## 11. The Auto-Rotate / Hover State Machine

```ts
let isHovered = false;

canvas.addEventListener('mouseenter', () => { isHovered = true; });
canvas.addEventListener('mouseleave', () => { isHovered = false; isDragging = false; });

function animate() {
  animId = requestAnimationFrame(animate);
  if (!isHovered) pivot.rotation.y += 0.006;
  renderer.render(scene, camera);
}
```

**Auto-rotation speed:**
```
0.006 rad/frame × 60 frame/s = 0.36 rad/s ≈ 20.6°/s
360° / 20.6°/s ≈ 17.5 seconds per full rotation
```

Slightly slower than the procedural turtle (27.5°/s) — more majestic, befitting the model viewer context.

The state machine has two states:

```
State: AUTO_ROTATE
  Entry: isHovered = false
  Action each frame: pivot.rotation.y += 0.006
  Transition to INTERACTIVE: mouseenter

State: INTERACTIVE
  Entry: isHovered = true
  Action each frame: nothing (rotation only changes on drag)
  Substate: IDLE (cursor = grab)
  Substate: DRAGGING (cursor = grabbing, rotation changes on pointermove)
  Transition to AUTO_ROTATE: mouseleave
```

`mouseleave` also sets `isDragging = false` so a drag that ends by moving off-canvas doesn't leave the model in a stuck state.

---

## 12. The Pivot Group and Why It Exists

```ts
const pivot = new THREE.Group();
scene.add(pivot);
// ... later ...
pivot.add(gltf.scene);
pivot.scale.setScalar(2 / maxDim);
pivot.rotation.x = 0.2;  // slight tilt to show shell top
```

The pivot serves three purposes:

1. **Centering**: as proven in section 5, scaling the pivot (not gltf.scene) correctly centers the model
2. **Unified transform**: all rotations (auto-rotate + drag) and the scale are applied to one object — gltf.scene is untouched after initial centering
3. **Tilt**: `rotation.x = 0.2 rad ≈ 11.5°` tilts the model forward slightly, so the camera's slightly-elevated perspective shows the top of the shell rather than looking exactly at the equator. Without this tilt, you'd see more of the underside than the shell.

The pivot's rotation from drag accumulates over time (Euler angle increases without bound). This is fine for display — Three.js normalizes angles internally for the matrix computation. It would be problematic for animation interpolation, but for a simple display model it doesn't matter.

---

## 13. Summary: The Complete Data Flow

```
1. HTTP request → scene.gltf (JSON) + scene.bin (binary) + textures (PNG/JPEG)
        ↓ parsed by GLTFLoader
2. GLTF node hierarchy (with quaternion rotations, translations)
        ↓ converted to THREE.Group / THREE.Mesh hierarchy
3. gltf.scene (placed inside pivot)
        ↓ Box3.setFromObject → AABB
4. center = (min + max) / 2
        ↓ gltf.scene.position.sub(center) — centering
5. pivot.scale = 2 / maxDim — fitting
        ↓ per-frame
6. if (!isHovered): pivot.rotation.y += 0.006 (auto-rotate)
   else if (dragging): pivot.rotation.y += dx × 0.01, pivot.rotation.x += dy × 0.01
        ↓ renderer.render(scene, camera)
7. Vertex shader: clip_pos = P × V × M × v_local
        ↓ fragment shader: PBR + textures + ACES tonemapping + sRGB output
8. Pixels on screen (320×340 canvas)
```
