# JhinBackground.svelte — Math Deep Dive

This document explains every mathematical operation in `src/lib/components/JhinBackground.svelte`. This component renders an animated League of Legends Jhin model as a fixed, semi-transparent background layer behind the bookings page. Prerequisites: read `01-threejs-core.md` and `04-gltf-models.md` first.

---

## 1. The Transparent Overlay Canvas

```ts
const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
renderer.setSize(w, h);
```

```html
<canvas class="fixed inset-0 z-[1] w-full h-full block pointer-events-none opacity-70">
```

The canvas is `fixed` and full-viewport, sitting at `z-index: 1` — above `Background.svelte`'s triangle mesh (`z-index: 0`) but below all page content (`z-index: 1+` via relative positioning).

Two transparency mechanisms work together:

**WebGL alpha (`alpha: true`)**: The WebGL context is created with an alpha channel. When no `setClearColor` is called with a solid colour, the renderer clears to `(0, 0, 0, 0)` each frame — fully transparent black. Pixels that Jhin does not cover remain transparent, allowing the triangle mesh from `Background.svelte` to show through.

**CSS opacity**: `opacity-70` applies `opacity: 0.7` to the entire canvas element. This multiplies every pixel's alpha (including Jhin's solid pixels) by 0.7, blending the whole character into the scene:

```
final_alpha = webgl_alpha × css_opacity
            = 1.0 × 0.7 = 0.70   (on Jhin's pixels)
            = 0.0 × 0.7 = 0.00   (on transparent pixels)
```

`pointer-events: none` ensures mouse events pass through the canvas to the underlying page content.

---

## 2. Camera and Frustum Placement

```ts
const camera = new THREE.PerspectiveCamera(45, w / h, 0.01, 1000);
camera.position.set(1.5, 1.0, 5);
camera.lookAt(0.5, 0, 0);
```

The camera is placed at `z = 5` and looks toward `(0.5, 0, 0)`. This slight offset to the right means the centre of the visible frustum is at x = 0.5, not x = 0.

**Frustum dimensions at z = 0** (where Jhin stands):

```
distance from camera to z=0 plane = 5

half_height = distance × tan(vFOV/2) = 5 × tan(22.5°) ≈ 5 × 0.4142 ≈ 2.07 units
half_width  = half_height × aspect  ≈ 2.07 × (16/9)  ≈ 3.68 units

Visible x range: [lookAt.x - half_width,  lookAt.x + half_width]
               = [0.5 - 3.68, 0.5 + 3.68] = [-3.18, 4.18]
Visible y range: [-2.07, 2.07]
```

### Pivot Placement — Top-Left

```ts
pivot.position.set(-3.2, 0.6, 0);
pivot.rotation.y = Math.PI / 2.5;
```

With the frustum spanning x ∈ [-3.18, 4.18] and y ∈ [-2.07, 2.07], placing the pivot at `(-3.2, 0.6)` puts Jhin near the left edge of the screen, upper half.

After `pivot.scale.setScalar(2 / maxDim)`, the model is 2 world units tall. Its bounding box in world space spans roughly:

```
x: [-3.2 - 1, -3.2 + 1] = [-4.2, -2.2]   (left edge visible from -3.18)
y: [0.6 - 1,  0.6 + 1]  = [-0.4,  1.6]   (well within [-2.07, 2.07])
```

The character is partially cropped on the left — intentionally, as it gives the impression of Jhin emerging from the edge of the screen.

**Y rotation**: `Math.PI / 2.5 ≈ 72°` rotates Jhin around the vertical axis so his side and partial back face the camera, fitting the three-quarter perspective look.

---

## 3. The AnimationMixer — Single Clip Loop

```ts
mixer = new THREE.AnimationMixer(gltf.scene);
const clip = THREE.AnimationClip.findByName(gltf.animations, 'jhin_spell4_idle.anm');
if (clip) {
    const action = mixer.clipAction(clip);
    action.setLoop(THREE.LoopRepeat, Infinity);
    action.play();
}
```

### AnimationMixer

`THREE.AnimationMixer` is the playback engine for skeletal animations. It manages a set of `AnimationAction` objects, each controlling one `AnimationClip` on one target object (here, `gltf.scene`).

The mixer must be updated every frame with the elapsed time:

```ts
const delta = clock.getDelta();   // seconds since last frame
mixer.update(delta);
```

`delta` is computed by `THREE.Clock.getDelta()`. On a 60 Hz display, `delta ≈ 0.01667 s`. The mixer uses this to advance the current playback time of every active action.

### AnimationClip and Keyframes

An `AnimationClip` contains a set of `KeyframeTrack` objects — one per bone channel (position, quaternion, scale). Each track stores:

```
times:  [t0, t1, t2, ...]     // keyframe timestamps in seconds
values: [v0, v1, v2, ...]     // property values at each timestamp
```

For a rotation track (quaternion), each `vi` is 4 values (x, y, z, w). Between keyframes, Three.js interpolates using SLERP (see `04-gltf-models.md` §2.2).

### LoopRepeat

`action.setLoop(THREE.LoopRepeat, Infinity)` causes the clip to restart from time 0 every time it reaches its end. The `jhin_spell4_idle.anm` clip is a pre-looped animation (start and end poses match), so the restart is seamless.

### `THREE.AnimationClip.findByName`

This is a static helper that searches `gltf.animations` (an array of `AnimationClip` objects) by the `name` property. Names come directly from the GLTF JSON's `animations[i].name` field, which in this asset is the original `.anm` filename from the League of Legends source: `"jhin_spell4_idle.anm"`.

---

## 4. The Render Loop

```ts
const clock = new THREE.Clock();

function tick() {
    raf = requestAnimationFrame(tick);
    const delta = clock.getDelta();
    mixer?.update(delta);
    renderer.render(scene, camera);
}
```

`requestAnimationFrame` schedules `tick` to run before the next display refresh, typically 60 times per second. The order within each frame is:

1. **Advance animation** (`mixer.update(delta)`): recomputes all bone matrices for the new time
2. **Render** (`renderer.render`): uploads updated matrices to the GPU, runs vertex/fragment shaders, composites to the canvas

`THREE.Clock.getDelta()` returns the wall-clock seconds elapsed since the last call to `getDelta()`. This makes the animation frame-rate independent — on a 30 Hz display, `delta ≈ 0.033` and bones advance twice as far per frame, keeping the same real-time speed.

---

## 5. Lighting

```ts
const ambient = new THREE.AmbientLight(0x1a3ecf, 0.4);
const key     = new THREE.DirectionalLight(0xe8edfc, 1.2);  // pos (−2, 4, 5)
const rim     = new THREE.DirectionalLight(0x1a3ecf, 1.2);  // pos (−4, 2, −3)
const fill    = new THREE.DirectionalLight(0x4060ff, 0.3);  // pos (0, −2, 2)
```

The lighting rig is the same three-point cyberpunk setup as `TurtleComparison.svelte` (see `04-gltf-models.md` §6), adapted for Jhin's position in the top-left:

- **Key** at `(-2, 4, 5)` — slightly to Jhin's right and above, providing the main illumination from the front
- **Rim** at `(-4, 2, -3)` — from Jhin's far left and behind, creating a blue edge highlight that separates him from the dark background
- **Fill** at `(0, -2, 2)` — below and in front, lifting the underside shadows to prevent pure black

The blue ambient `0x1a3ecf` at 0.4 intensity sets a base tone matching the design system's primary colour (`--color-primary`).

---

## 6. Cleanup

```ts
return () => {
    cancelAnimationFrame(raf);
    window.removeEventListener('resize', onResize);
    mixer?.stopAllAction();
    renderer.dispose();
};
```

Svelte's `onMount` cleanup function runs when the component is destroyed (navigating away from `/bookings`). The cleanup sequence:

1. `cancelAnimationFrame(raf)` — stops the render loop so no further GPU work is submitted
2. `removeEventListener` — prevents the resize handler from firing on a destroyed component
3. `mixer.stopAllAction()` — stops all animation actions and releases their references
4. `renderer.dispose()` — releases the WebGL context and all GPU resources (buffers, textures, shaders)

Without this cleanup, the WebGL context would leak, eventually causing the browser to warn about too many active contexts.

---

## 7. Summary: Complete Data Flow

```
1. unzip jhin_league_of_legends.zip → static/models/jhin/
        ↓
2. GLTFLoader fetches /models/jhin/scene.gltf + scene.bin + textures/
        ↓ parsed into THREE.Group + 36 AnimationClips
3. Box3.setFromObject → AABB center + maxDim
   gltf.scene.position.sub(center)         — centers model at origin
   pivot.scale.setScalar(2 / maxDim)       — fits to 2 world units
   pivot.position = (-3.2, 0.6, 0)         — top-left of frustum
   pivot.rotation.y = Math.PI / 2.5        — three-quarter angle
        ↓
4. AnimationMixer.clipAction('jhin_spell4_idle.anm')
   action.setLoop(LoopRepeat, Infinity)
   action.play()
        ↓ per frame
5. clock.getDelta() → mixer.update(delta)  — advance bone poses
   renderer.render(scene, camera)          — GPU rasterizes Jhin
        ↓
6. Canvas alpha=true → transparent pixels behind Jhin
   CSS opacity-70 → 70% opaque overall
   z-[1] → above triangle mesh, below page content
```
