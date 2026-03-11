# YasuoBackground.svelte — Math Deep Dive

This document explains every mathematical operation in `src/lib/components/YasuoBackground.svelte`. This component renders an animated League of Legends Yasuo model in the bottom-left corner of the bookings page, playing an intro animation once before transitioning into a looped dance. Prerequisites: read `01-threejs-core.md`, `04-gltf-models.md`, and `05-jhin.md` first — this document focuses on what is new: multi-clip animation sequencing.

---

## 1. Canvas and Renderer Setup

Identical to `JhinBackground.svelte` — see `05-jhin.md` §1. The canvas is `fixed inset-0 z-[1] opacity-70 pointer-events-none`. Two renderers (`JhinBackground` and `YasuoBackground`) coexist on the page, each owning a separate WebGL context and canvas element. The browser composites them in DOM order: Jhin's canvas sits below Yasuo's within `z-index: 1`, both below the page content.

---

## 2. Camera and Frustum Placement

```ts
const camera = new THREE.PerspectiveCamera(45, w / h, 0.01, 1000);
camera.position.set(0, 0, 5);
camera.lookAt(0, 0, 0);
```

The camera looks directly at the world origin. With `vFOV = 45°` and the camera at `z = 5`:

```
half_height = 5 × tan(22.5°) ≈ 2.07 units
half_width  = 2.07 × aspect  ≈ 3.68 units   (at 16:9)

Visible x range: [-3.68, +3.68]
Visible y range: [-2.07, +2.07]
```

### Pivot Placement — Bottom-Left

```ts
pivot.position.set(2.5, -0.5, 0);
```

After scaling to 2 world units tall (`pivot.scale.setScalar(2 / maxDim)`), the model's bounding box spans roughly ±1 unit from its center:

```
x: [2.5 - 1, 2.5 + 1] = [1.5, 3.5]    (right edge of screen at +3.68)
y: [-0.5 - 1, -0.5 + 1] = [-1.5, 0.5]  (bottom edge at -2.07)
```

Yasuo occupies the lower-right quadrant: his feet are near `y = -1.5` (well above the bottom clip at `-2.07`) and his head reaches `y ≈ 0.5` (mid-screen height). His body is partially cropped on the right, mirroring the same edge-emergence effect as Jhin on the opposite side.

No Y-axis rotation is applied — Yasuo faces forward (toward the camera), which suits his dancing animation.

---

## 3. Two-Clip Animation Sequencing

This is the primary new concept in this component. The goal: play `yasuo_dance_in.anm` exactly once, then seamlessly transition into `yasuo_dance_loop.anm` repeating forever.

```ts
const clipIn   = THREE.AnimationClip.findByName(gltf.animations, 'yasuo_dance_in.anm');
const clipLoop = THREE.AnimationClip.findByName(gltf.animations, 'yasuo_dance_loop.anm');

const actionIn = mixer.clipAction(clipIn);
actionIn.setLoop(THREE.LoopOnce, 1);
actionIn.clampWhenFinished = true;
actionIn.play();

mixer.addEventListener('finished', (e) => {
    if (e.action === actionIn) {
        const actionLoop = mixer.clipAction(clipLoop);
        actionLoop.setLoop(THREE.LoopRepeat, Infinity);
        actionIn.crossFadeTo(actionLoop, 0.3, false);
        actionLoop.play();
    }
});
```

### 3.1 LoopOnce and clampWhenFinished

`action.setLoop(THREE.LoopOnce, 1)` plays the clip from start to end exactly once. When the playback time reaches the clip duration, the action stops advancing.

`clampWhenFinished = true` holds the final pose after the clip ends, instead of snapping back to the bind pose (T-pose). This is critical: without it, Yasuo would flash to T-pose for the one or two frames between the `finished` event firing and the loop action starting.

```
Timeline:
  t=0          t=clipIn.duration   t=clipIn.duration + 0.3
  |--------dance_in playing--------|---crossfade zone---|---dance_loop playing...
                                   ↑
                             'finished' event fires here
```

### 3.2 The `finished` Event

`mixer.addEventListener('finished', callback)` fires when any action on this mixer reaches its end with `LoopOnce`. The event object `e` contains `e.action` — the `AnimationAction` that finished. The guard `if (e.action === actionIn)` is important: if other clips were ever added with `LoopOnce`, the handler would not accidentally trigger on them.

### 3.3 crossFadeTo — Blending Between Clips

```ts
actionIn.crossFadeTo(actionLoop, 0.3, false);
actionLoop.play();
```

`crossFadeTo(target, duration, warp)` sets up a smooth blend between two actions over `duration` seconds (here, 0.3 s = 18 frames at 60 Hz):

**Weight interpolation**: Each `AnimationAction` has a weight in `[0, 1]` that controls how much it contributes to the final bone pose. During the crossfade:

```
t = elapsed time since crossfade started ∈ [0, 0.3]

weight_actionIn   = 1 - t/0.3    (fades from 1 → 0)
weight_actionLoop = t/0.3         (fades from 0 → 1)
```

The mixer blends bone transforms using these weights:

```
final_bone_quaternion = SLERP(actionIn_bone_quat, actionLoop_bone_quat, weight_actionLoop)
final_bone_position   = LERP(actionIn_bone_pos,   actionLoop_bone_pos,   weight_actionLoop)
```

SLERP (Spherical Linear Interpolation) for quaternions ensures smooth, gimbal-lock-free rotation blending (see `04-gltf-models.md` §2.2).

**warp = false**: The third argument controls **time warping** — whether to adjust each action's playback speed so both clips reach a "natural" transition point at the same moment. `false` disables warping: both clips play at their natural speed during the crossfade. For a dance transition this is appropriate — both animations run at their authored tempo.

**`actionLoop.play()` timing**: `clipAction` returns the action in a stopped state. Calling `play()` immediately after `crossFadeTo` starts the loop action at time 0, with its weight at 0 (controlled by the crossfade). After 0.3 s, the crossfade completes: `actionIn` reaches weight 0 and is automatically deactivated, `actionLoop` reaches weight 1 and becomes the sole driver of the skeleton.

### 3.4 Fallback

```ts
} else if (clipLoop) {
    const actionLoop = mixer.clipAction(clipLoop);
    actionLoop.setLoop(THREE.LoopRepeat, Infinity);
    actionLoop.play();
}
```

If `yasuo_dance_in.anm` is not found in the GLTF (e.g., the asset was swapped), the loop clip plays directly. This prevents the component from silently rendering a motionless T-pose.

---

## 4. Lighting

```ts
const ambient = new THREE.AmbientLight(0x1a3ecf, 0.4);
const key     = new THREE.DirectionalLight(0xe8edfc, 1.2);  // pos (−2, 4, 5)
const rim     = new THREE.DirectionalLight(0x1a3ecf, 1.2);  // pos (+4, 2, −3)
const fill    = new THREE.DirectionalLight(0x4060ff, 0.3);  // pos (0, −2, 2)
```

The same three-point cyberpunk rig as Jhin (see `05-jhin.md` §5), with the rim light mirrored to `(+4, 2, -3)` — coming from Yasuo's right rear — since he faces the opposite direction from Jhin. This keeps the blue rim highlight consistent regardless of character orientation.

---

## 5. Two WebGL Contexts Coexisting

Both `JhinBackground` and `YasuoBackground` mount simultaneously on the bookings page. Each creates an independent:

- WebGL context (one per canvas element)
- Scene graph
- AnimationMixer
- `requestAnimationFrame` loop

The browser serialises WebGL draw calls on the GPU — there is no true parallelism — but since each renderer calls `renderer.render()` in its own rAF callback, and both are scheduled at the same display rate, they interleave efficiently.

**Context limit**: browsers enforce a limit of ~8–16 WebGL contexts per page (Chrome enforces 16). With `Background.svelte` (1), `JhinBackground` (1), and `YasuoBackground` (1), the page uses 3 — well within the limit.

**Performance**: each canvas redraws the full viewport every frame at `devicePixelRatio` resolution. On a 1080p display at DPR=2, each renderer processes 4K pixels per frame. This is GPU-bound work; modern GPUs handle it without issue, but devices with very low-end GPUs may experience frame drops.

---

## 6. Summary: Complete Data Flow

```
1. unzip yasuo_league_of_legends_character.zip → static/models/yasuo/
        ↓
2. GLTFLoader fetches /models/yasuo/scene.gltf + scene.bin + textures/
        ↓ parsed into THREE.Group + 44 AnimationClips
3. Box3.setFromObject → AABB center + maxDim
   gltf.scene.position.sub(center)         — centers model at origin
   pivot.scale.setScalar(2 / maxDim)       — fits to 2 world units
   pivot.position = (2.5, -0.5, 0)         — bottom-right of frustum
        ↓
4. AnimationMixer setup:
   actionIn  = clipAction('yasuo_dance_in.anm'),  LoopOnce,   clampWhenFinished
   actionIn.play()
        ↓ when actionIn finishes
5. mixer 'finished' event:
   actionLoop = clipAction('yasuo_dance_loop.anm'), LoopRepeat
   actionIn.crossFadeTo(actionLoop, 0.3s, false)
   actionLoop.play()
        ↓ per frame
6. clock.getDelta() → mixer.update(delta)  — advance + blend bone poses
   renderer.render(scene, camera)          — GPU rasterizes Yasuo
        ↓
7. Canvas alpha=true → transparent pixels around Yasuo
   CSS opacity-70 → 70% opaque overall
   z-[1] → composited above Jhin's canvas and Background.svelte
```
