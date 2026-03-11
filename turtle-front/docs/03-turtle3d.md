# Turtle3D.svelte — Math Deep Dive

This document explains every mathematical operation in `src/lib/components/Turtle3D.svelte`. This is the procedural turtle built entirely from Three.js primitive geometries — no external model file. Prerequisites: read `01-threejs-core.md` first.

---

## 1. The Camera

```ts
const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
camera.position.set(0, 1.5, 5.5);
camera.lookAt(0, 0, 0);
```

### Field of View

`45` degrees is the **vertical field of view** — how wide the camera's "lens" is, measured top to bottom. The horizontal FOV is derived from it using the aspect ratio.

The camera's view frustum is a truncated pyramid. The **half-height** of the near clip plane is:

```
half_height = near × tan(fov/2) = 0.1 × tan(22.5°) ≈ 0.1 × 0.4142 ≈ 0.04142
```

At the far plane (z = -100 from camera):

```
half_height_far = 100 × tan(22.5°) ≈ 41.42 world units
```

Everything inside this expanding pyramid is visible; everything outside is clipped.

45° is a deliberately narrow FOV (typical camera lenses are 50–60° equivalent). A narrower FOV produces a more telephoto look — less perspective distortion, objects appear flatter and more "dignified". Wide FOV (90°+) creates fisheye-like distortion.

### Aspect Ratio

`aspect = 1` means width = height (square canvas 280×280). The horizontal FOV is then equal to the vertical FOV: also 45°.

The projection matrix scales X by `1/aspect` so that:
- A circle in world space appears circular on screen (not stretched into an ellipse)
- Objects at the center don't appear to lean sideways

### Camera Placement

```
camera.position.set(0, 1.5, 5.5)
camera.lookAt(0, 0, 0)
```

The camera is 5.5 units in front of the scene (positive Z), and 1.5 units above the XZ plane. `lookAt(0, 0, 0)` computes the rotation needed to aim at the world origin.

This elevation angle can be computed:

```
elevation = arctan(1.5 / 5.5) ≈ arctan(0.2727) ≈ 15.3°
```

The camera looks slightly downward at about 15°, giving a mild bird's-eye view that shows the turtle's shell curvature. If the camera were at `(0, 0, 5.5)` (same height as the turtle), you'd see it head-on from the side.

The `lookAt` function internally builds a rotation matrix from three vectors:
- **forward** = normalize(target - eye) = normalize((0,0,0) - (0,1.5,5.5)) = (-0, -1.5, -5.5) normalized
- **up** = (0, 1, 0) (world up)
- **right** = cross(forward, up) (perpendicular to both)

These three orthonormal vectors form the columns of the camera's orientation matrix.

---

## 2. Lighting

### AmbientLight

```ts
scene.add(new THREE.AmbientLight(0x1a3ecf, 0.4));
```

Ambient light adds a **constant colour** to every fragment, regardless of the surface normal or light position. It simulates the indirect bounced light that fills shadowed areas in real environments.

```
fragment_colour += ambient_colour × ambient_intensity × surface_albedo
                 = #1a3ecf × 0.4 × surface_colour
```

`#1a3ecf` is the royal blue — 27/256 red, 62/256 green, 207/256 blue in linear space. At 0.4 intensity, this tints everything with a faint blue glow, reinforcing the cyberpunk palette.

### PointLight (Key Light)

```ts
const keyLight = new THREE.PointLight(0x4466ff, 2.0, 20);
keyLight.position.set(3, 4, 3);
```

A point light radiates in all directions from a single point, like a bare bulb. Three.js uses **physically-based attenuation**:

```
attenuation = 1 / (1 + (distance / range)²)
```

Where `range = 20` (the third parameter) is the maximum effective distance. At `distance = 0`: attenuation = 1 (full intensity). At `distance = range`: attenuation = 0.5. Beyond range: Three.js smoothly fades to 0 to avoid infinite reach.

The light is at `(3, 4, 3)` — upper-right-front of the scene. This creates the main highlights on the turtle's upper shell, right side of the head, and front-right leg.

**Intensity 2.0**: brighter than default. In Three.js's physically based lighting, the unit of point light intensity is **candela** (luminous intensity). A value of 2.0 means it's 2× the reference brightness.

### PointLight (Rim Light)

```ts
const rimLight = new THREE.PointLight(0x0d1a5c, 0.8, 15);
rimLight.position.set(-2, -2, -3);
```

A rim light behind and below the subject (`-2, -2, -3` is lower-left-rear). It's a very dark navy (`#0d1a5c`) — essentially a dim blue glow that barely outlines the bottom-left edges of the turtle.

The purpose is to prevent the turtle from "disappearing" into the dark background on its shadowed side. Without it, the back-left of the shell would be pure black and blend into the dark canvas.

### How Lighting Combines in MeshStandardMaterial

For each fragment (pixel on the surface), the light contribution from each light source is:

```
L_out = (diffuse + specular) × attenuation × light_colour

diffuse  = albedo × max(0, dot(N, L)) × (1 - metalness)
specular = GGX_BRDF(N, H, roughness) × mix(0.04, albedo, metalness)
```

Where:
- `N` = surface normal (unit vector perpendicular to the surface)
- `L` = unit vector from fragment to light
- `H` = half-vector = normalize(L + V), where V = vector to camera
- `dot(N, L)` = Lambert's cosine law — surfaces facing the light are fully lit; surfaces at 90° are unlit
- `GGX_BRDF` = the **GGX microfacet specular model** — more detail in the materials section below

Total fragment colour:

```
colour = ambient + Σ(over all lights) L_out
```

---

## 3. The Shell: Partial UV Sphere

```ts
const shellGeo = new THREE.SphereGeometry(1.0, 10, 7, 0, Math.PI * 2, 0, Math.PI * 0.6);
shell.scale.set(1.0, 0.55, 0.85);
```

### SphereGeometry Parameters

`SphereGeometry(radius, widthSegments, heightSegments, phiStart, phiLength, thetaStart, thetaLength)`

| Param | Value | Meaning |
|-------|-------|---------|
| radius | 1.0 | sphere radius in world units |
| widthSegments | 10 | number of vertical slices (longitude divisions) |
| heightSegments | 7 | number of horizontal rings (latitude divisions) |
| phiStart | 0 | start longitude angle (radians) |
| phiLength | π×2 | total longitude sweep — full 360° |
| thetaStart | 0 | start polar angle from +Y |
| thetaLength | π×0.6 | total polar sweep — 108° from top |

### UV Sphere Parametric Formula

A sphere of radius `r` is parametrically defined by two angles:
- **φ (phi)**: longitude, runs around the equator, range `[0, 2π]`
- **θ (theta)**: polar angle from the +Y pole, range `[0, π]`

The 3D position of each vertex:

```
x = r × sin(θ) × cos(φ)
y = r × cos(θ)
z = r × sin(θ) × sin(φ)
```

At θ=0: `x=0, y=r, z=0` → top pole (+Y).
At θ=π: `x=0, y=-r, z=0` → bottom pole (-Y).
At θ=π/2: `y=0`, points lie on the equator.

For the shell, `thetaLength = π × 0.6 = 0.6π = 108°`. The sphere is only generated from the top pole (θ=0) to 108° down — slightly past the equator (90°). This creates the upper dome of the shell that covers the top and sides of the turtle's body, with the opening pointing downward.

### Non-Uniform Scaling → Ellipsoid

```ts
shell.scale.set(1.0, 0.55, 0.85);
```

Applying different scale factors along each axis transforms the sphere into a **scalene ellipsoid** — an ellipsoid with different radii on all three axes:

```
effective radius X = 1.0 × 1.0 = 1.0 world unit
effective radius Y = 1.0 × 0.55 = 0.55 world units  (flattened vertically)
effective radius Z = 1.0 × 0.85 = 0.85 world units  (slightly compressed front-to-back)
```

The formula for a scalene ellipsoid at scale `(sx, sy, sz)` applied to a unit sphere:

```
(x/sx)² + (y/sy)² + (z/sz)² = 1
```

Geometrically: every vertex `(x, y, z)` of the original unit sphere becomes `(sx·x, sy·y, sz·z)`. The Y compression (0.55) flattens the dome to give the turtle its characteristic low-profile shell.

**Important subtlety**: scaling in the model matrix (as Three.js does) scales the *positions* but also changes the surface *normals* in a non-trivial way. To keep normals perpendicular to the scaled surface, Three.js uses the **inverse-transpose** of the model matrix (`mat3(transpose(inverse(modelMatrix)))`), computed automatically in the vertex shader.

---

## 4. The Plastron: Inverted Partial Sphere

```ts
const plastronGeo = new THREE.SphereGeometry(0.9, 8, 5, 0, Math.PI * 2, Math.PI * 0.4, Math.PI * 0.6);
plastron.scale.set(0.88, 0.3, 0.75);
plastron.position.set(0, -0.06, 0);
plastron.rotation.x = Math.PI;
```

### The Theta Range

`thetaStart = π × 0.4 = 72°`, `thetaLength = π × 0.6 = 108°`. So the sphere is generated from 72° to 72°+108° = 180° — from somewhat south of the equator down to the bottom pole.

This creates a cap on the **lower hemisphere**, starting at latitude 72° from the top. Think of it as cutting away the top 72° and the middle equatorial band, keeping only the lower portion.

### Rotation by π

`plastron.rotation.x = Math.PI` flips the geometry upside-down (180° rotation around X). Combined with the theta range, this makes the plastron's curved side face downward, creating the flat belly plate of the turtle.

### Why a Separate Sphere Instead of Inverting the Shell?

The shell opens downward (thetaLength stops at 108°, leaving an open bottom). The plastron is a differently-sized sphere with different theta range, positioned to sit just below the shell, creating the illusion of the two halves of the turtle's shell.

---

## 5. The Neck: Tapered Cylinder

```ts
const neckGeo = new THREE.CylinderGeometry(0.12, 0.16, 0.35, 6);
neck.position.set(0, 0.1, 0.72);
neck.rotation.x = -Math.PI * 0.25;
```

`CylinderGeometry(radiusTop, radiusBottom, height, radialSegments)`

The neck is slightly wider at the bottom (0.16) than at the top (0.12), simulating the thicker base where the neck meets the body. The parametric formula for a tapered cylinder:

```
r(y) = radiusBottom + (radiusTop - radiusBottom) × (y + height/2) / height

For y ∈ [-height/2, height/2]:
  at y = -height/2 (bottom): r = radiusBottom = 0.16
  at y = +height/2 (top):    r = radiusTop    = 0.12

Vertex position at height y, angle φ:
  x = r(y) × cos(φ)
  z = r(y) × sin(φ)
  y = y
```

`radialSegments = 6` creates a hexagonal cross-section (6 vertical panels). Fewer segments = fewer triangles = more angular appearance.

### Neck Rotation

`rotation.x = -Math.PI × 0.25 = -45°`

Rotating around X by -45° tilts the top of the cylinder forward (-Z direction) and the bottom backward (+Z). Since the neck is positioned at `z = 0.72` (in front of the shell), this makes the neck angle outward and forward — the turtle's neck extending out of the shell at a realistic 45° angle.

The neck's default orientation is along the Y axis (top to bottom). After `rotation.x = -π/4`:

```
new_top    = Rx(-π/4) × (0, 0.175, 0)  = (0, 0.175×cos(-π/4), 0.175×sin(-π/4))
           = (0, 0.1237, -0.1237)    → front-upper
new_bottom = (0, -0.1237, 0.1237)    → rear-lower
```

Combined with the translation to `(0, 0.1, 0.72)`, the neck top ends up near `(0, 0.22, 0.60)` — where the head sits.

---

## 6. The Head: Scaled Sphere

```ts
const headGeo = new THREE.SphereGeometry(0.22, 8, 6);
head.scale.set(1.0, 0.9, 1.1);
head.position.set(0, 0.25, 1.05);
```

An 8×6 UV sphere scaled to an oval: slightly flattened vertically (0.9), elongated front-to-back (1.1). At radius 0.22 with those scales:

```
effective width (X)  = 0.22 × 1.0 = 0.22 units
effective height (Y) = 0.22 × 0.9 = 0.198 units
effective depth (Z)  = 0.22 × 1.1 = 0.242 units
```

The head is slightly "snout-like" — elongated in Z. It's positioned at `(0, 0.25, 1.05)` — centered, slightly above the shell center, and in front of where the neck ends.

---

## 7. The Eyes: Tiny Spheres

```ts
const eyeGeo = new THREE.SphereGeometry(0.05, 5, 4);
leftEye.position.set(-0.1, 0.33, 1.2);
rightEye.position.set( 0.1, 0.33, 1.2);
```

5×4 sphere (very low poly — practically an icosahedron approximation) at radius 0.05. Placed symmetrically ±0.1 in X, slightly above the head center (Y=0.33 vs head Y=0.25), and in front of the head (Z=1.2 vs head Z=1.05).

The eyes use `MeshBasicMaterial({ color: 0x1a3ecf })` — unlit, always the exact royal blue regardless of light. This is intentional: they should appear to glow.

---

## 8. The Legs: Capsule Geometry

```ts
const frontLegGeo = new THREE.CapsuleGeometry(0.1, 0.45, 4, 6);
const rearLegGeo  = new THREE.CapsuleGeometry(0.1, 0.38, 4, 6);
```

`CapsuleGeometry(radius, length, capSegments, radialSegments)`

A capsule is a cylinder capped by two hemispheres. The total height = `length + 2 × radius`:

```
Front leg total height = 0.45 + 2 × 0.1 = 0.65 units
Rear leg total height  = 0.38 + 2 × 0.1 = 0.58 units
```

`capSegments = 4`: each hemisphere is approximated by 4 rings of quads. `radialSegments = 6`: hexagonal cross-section.

The capsule vertex formula:

```
Cylinder body (|y| < length/2):
  x = radius × cos(φ)
  z = radius × sin(φ)
  y = y

Top hemisphere (y > length/2), offset by length/2:
  x = radius × sin(θ) × cos(φ)
  y = length/2 + radius × cos(θ)    (θ from 0 to π/2)
  z = radius × sin(θ) × sin(φ)

Bottom hemisphere: mirror of top
```

### Leg Positioning and Rotation

```ts
const legDefs = [
  { geo: frontLegGeo, pos: [-0.8, -0.05, 0.5],  rz: Math.PI * 0.35, rx: -Math.PI * 0.15 },
  { geo: frontLegGeo, pos: [ 0.8, -0.05, 0.5],  rz: -Math.PI * 0.35, rx: -Math.PI * 0.15 },
  { geo: rearLegGeo,  pos: [-0.75, -0.05, -0.48], rz: Math.PI * 0.3,  rx: Math.PI * 0.12 },
  { geo: rearLegGeo,  pos: [ 0.75, -0.05, -0.48], rz: -Math.PI * 0.3,  rx: Math.PI * 0.12 },
];
```

Each leg has two rotations:

**rz (Z rotation — spreads legs sideways):**
- Left front: `+π × 0.35 = +63°` → tilts the capsule's top to the left (away from body)
- Right front: `-63°` → mirror
- The ± symmetry creates the "swimming fins spread outward" look

**rx (X rotation — angles legs forward/backward):**
- Front legs: `rx = -π × 0.15 = -27°` → tips the top of the capsule forward (-Z)
- Rear legs: `rx = +π × 0.12 = +21.6°` → tips the top backward (+Z)

These two rotations applied in sequence produce legs angled both sideways and tilted relative to the body, as a sea turtle's flippers naturally are.

**Euler order**: Three.js applies rotations in **XYZ order** (`R = Rz · Ry · Rx`). When you set both `rotation.x` and `rotation.z`, the X rotation is applied first in local space, then Z. This means `rz` rotates the already-rx-rotated capsule — the combined effect is not simply two independent tilts. The specific values were tuned visually.

---

## 9. The Tail: Tapered Cylinder

```ts
const tailGeo = new THREE.CylinderGeometry(0.04, 0.09, 0.28, 5);
tail.position.set(0, 0.0, -0.88);
tail.rotation.x = Math.PI * 0.25;
```

Small tapered cylinder (wider at base 0.09, narrow at tip 0.04), 5-sided (pentagonal cross-section). Positioned behind the shell at Z = -0.88.

`rotation.x = +π × 0.25 = +45°` tips the top of the tail upward and backward (since positive X rotation moves the +Y end toward +Z). The tail naturally points up and slightly backward from the body.

---

## 10. The Wireframe Shell Overlay

```ts
const shellWire = new THREE.Mesh(shellGeo, shellWireMat);
shellWire.scale.copy(shell.scale);
shellWire.position.copy(shell.position);
```

The wireframe uses the **exact same geometry** as the solid shell (`shellGeo`). Two meshes occupy the same position. Three.js renders both:

1. The solid `MeshStandardMaterial` shell — lit, dark metallic
2. The `MeshBasicMaterial` wireframe — unlit, royal blue at 35% opacity

The wireframe is drawn on top because Three.js renders opaque objects first, then transparent ones (sorted by distance). Since `shellWireMat` has `transparent: true, opacity: 0.35`, it's rendered in the transparent pass.

**Polygon offset**: In theory, two meshes at exactly the same Z depth cause **Z-fighting** — the depth buffer alternates between which mesh is "in front", creating flickering. Three.js mitigates this internally, and since the wireframe has no fill (only edges), the visual result is a clean overlay.

---

## 11. MeshStandardMaterial: PBR

```ts
const shellBodyMat = new THREE.MeshStandardMaterial({
  color: 0x111318,
  metalness: 0.6,
  roughness: 0.4,
  emissive: 0x040812
});
```

**PBR (Physically Based Rendering)** is a shading model that approximates how real materials interact with light using two parameters:

### Metalness

Controls whether the surface behaves like a **metal** or a **dielectric** (non-metal):

- `metalness = 0` (plastic, wood, skin): diffuse reflection, white specular highlights
- `metalness = 1` (gold, steel, chrome): no diffuse, coloured specular highlights, high reflectivity

At `metalness = 0.6` (shell): 60% metallic behaviour. The specular highlights take on the surface colour (#111318, very dark), producing subtle dark-tinted reflections — a "dark metallic" look.

The math blends between two reflection modes:

```
base_reflectance_F0 = mix(0.04, albedo, metalness)
```

`0.04` is the Fresnel reflectance at normal incidence for typical dielectrics (~4%). For metals, F0 equals the albedo (the surface colour is the specular colour).

### Roughness and GGX Microfacet

At `roughness = 0.4`: moderately smooth surface. Rough surfaces have many microscopic facets pointing in random directions, spreading specular reflections into a wide highlight. Smooth surfaces concentrate the highlight into a sharp, bright spot.

The specular term uses the **GGX (Trowbridge-Reitz) distribution**:

```
D_GGX(N, H, α) = α² / (π × ((dot(N,H))² × (α²-1) + 1)²)
```

Where `α = roughness²` (remapped for perceptual linearity), N = normal, H = half-vector. This gives the probability that microfacets are aligned to reflect light toward the camera.

The complete specular BRDF (Cook-Torrance):

```
f_specular = D(N,H,α) × G(N,L,V,α) × F(V,H,F0)
             ─────────────────────────────────────
                       4 × dot(N,L) × dot(N,V)
```

Where:
- `G` = Geometry/Shadowing term (Smith GGX): accounts for microfacets occluding each other
- `F` = Fresnel term (Schlick approximation): more reflective at grazing angles

```
F(cosθ, F0) = F0 + (1 - F0) × (1 - cosθ)^5
```

### Emissive

```ts
emissive: 0x040812
```

A very dark navy-blue additive term, independent of lighting. It makes the shell slightly self-illuminated — never completely black even in total darkness. The emissive contributes:

```
fragment_colour += emissive_colour × emissive_intensity
```

At `#040812` (4/255 red, 8/255 green, 18/255 blue), this is barely perceptible but prevents the shell from looking flat-black in shadowed areas.

---

## 12. The Group and Y-Axis Animation

```ts
const turtleGroup = new THREE.Group();
// ... add all meshes to turtleGroup ...
scene.add(turtleGroup);

function tick() {
  raf = requestAnimationFrame(tick);
  turtleGroup.rotation.y += 0.008;
  renderer.render(scene, camera);
}
```

`turtleGroup.rotation.y` stores an Euler angle in radians. Each frame, 0.008 radians are added.

**Speed calculation:**
```
angular_velocity = 0.008 rad/frame × 60 frames/sec = 0.48 rad/sec
                 = 0.48 × (180/π) deg/sec ≈ 27.5°/sec
                 = 360° / 27.5°/sec ≈ 13.1 seconds per full rotation
```

The Ry rotation matrix (from section 4 of `01-threejs-core.md`):

```
Ry(θ) = ┌  cos θ   0   sin θ  0 ┐
         │    0     1     0    0 │
         │ -sin θ   0   cos θ  0 │
         └    0     0     0    1 ┘
```

As θ increases, each child mesh's world X and Z coordinates trace a circle:

```
world_x = local_x × cos θ + local_z × sin θ
world_z = -local_x × sin θ + local_z × cos θ
```

The Y coordinate is unaffected (rotating around Y doesn't change height). This is why all parts of the turtle maintain their vertical position while rotating — the shell stays at Y=0.1, the head at Y=0.25, etc.

**The hierarchy benefit**: without a group, you would need to apply the same rotation matrix to every mesh independently and keep them synchronized. With a group, you set one rotation and the transform propagates to all children through the scene graph.

---

## 13. Complete Transform Chain for a Leg Vertex

Let's trace a single vertex from a front-left leg all the way to screen pixels.

Given:
- Leg capsule vertex at local position `(0.1, 0.3, 0)` (on the right edge, upper portion)
- `rotation.z = π × 0.35 = 1.0996 rad`, `rotation.x = -π × 0.15 = -0.4712 rad`
- `position = (-0.8, -0.05, 0.5)`
- turtleGroup `rotation.y = θ` (changing each frame)

**Step 1: Scale** (default 1,1,1 — identity, no change)

**Step 2: Apply Rx** (x rotation = -0.4712 rad):
```
Rx(-0.4712) × (0.1, 0.3, 0) = (0.1, 0.3×cos(-0.4712), -0.3×sin(-0.4712))
                              = (0.1, 0.271, 0.137)
```

**Step 3: Apply Rz** (z rotation = +1.0996 rad):
```
Rz(1.0996) × (0.1, 0.271, 0.137) = (0.1×cos(1.0996) - 0.271×sin(1.0996), ...)
cos(1.0996) ≈ 0.454, sin(1.0996) ≈ 0.891
x' = 0.1×0.454 - 0.271×0.891 = 0.0454 - 0.2415 = -0.196
y' = 0.1×0.891 + 0.271×0.454 = 0.0891 + 0.123 = 0.212
z' = 0.137 (unchanged by Rz)
Result: (-0.196, 0.212, 0.137)
```

**Step 4: Translate** (add leg position):
```
(-0.196 + (-0.8), 0.212 + (-0.05), 0.137 + 0.5) = (-0.996, 0.162, 0.637)
```
This is the vertex in turtleGroup's local space.

**Step 5: Apply turtleGroup Ry(θ)**:
```
x'' = -0.996 × cos θ + 0.637 × sin θ
z'' = 0.996 × sin θ + 0.637 × cos θ
y'' = 0.162 (unchanged)
```
This is the vertex in world space.

**Step 6: View matrix** (camera at (0, 1.5, 5.5) looking at origin):

The view matrix transforms world space to camera space. The exact matrix depends on `lookAt`. Conceptually: everything shifts so the camera is at the origin looking down -Z.

**Step 7: Projection matrix** (PerspectiveCamera, fov=45, aspect=1, near=0.1, far=100):

The vertex is projected into NDC [-1,1]³.

**Step 8: Viewport transform** → pixel coordinates on the 280×280 canvas.

All of steps 6–8 happen in the GPU vertex shader in a single matrix multiply.
