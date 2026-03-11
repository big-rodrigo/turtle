# Landing Page UX — Design & Animation Audit

## What's Wrong With the Current Approach

### 1. Two spinning turtles is a toy, not a story
The "FROM THIS → TO THIS" layout tells the right story conceptually — you start as Squirt, you become the Armored Titan — but the execution makes it passive. Two boxes sitting side by side, both spinning, with no causal relationship between them. The visitor doesn't *feel* the transformation; they just read a label.

### 2. The interaction is accidental
Hovering to stop rotation and dragging to spin is discoverable only by accident. There's no affordance, no invitation. Most visitors will never discover it. Worse, the gesture (drag-to-spin a cute turtle) reads as a toy, which undercuts the serious "grow as a professional" pitch.

### 3. GSAP is doing CSS's job
The entire animation budget right now is: stagger-in fade from `y: 20` on mount. That's a `@keyframes` animation — it doesn't need GSAP. GSAP's real power is in coordinated timelines, scroll-linked progressions, and tweening non-CSS values (Three.js `rotation.y`, `camera.position.z`, number counters). None of that is being used.

### 4. No narrative arc, no sections
A great landing page is a one-page sales pitch with a beginning, middle, and end: **hook → value → proof → CTA**. Right now there's only the hook (barely), a tagline, and a CTA. There's no reason to scroll, no argument being built.

---

## Proposed Directions/Sections

These are ordered from **highest impact** to most experimental. They can also be combined.

---

### Section A — Cinematic Boot Sequence + Single Hero Model *(Recommended)*

**The idea:** Replace the two-turtle side-by-side with a single cinematic entrance. The Armored Titan — the aspirational end state — fills the center of the screen. Before it appears, a terminal-style boot sequence runs: a few lines of monospace system text flash in one by one, then the model materializes with a dramatic GSAP-driven scale + glow reveal.

**Why it works:** You're leading with the prize, not the journey. The visitor's first emotion is "I want to be that." The boot sequence is a natural fit for the cyberpunk theme and creates 2–3 seconds of *anticipation* before the model appears — which makes the reveal land harder.

**Implementation breakdown:**

1. **Boot sequence** — An array of strings (`["// TURTLE COACHING OS v1.0.0", "LOADING COACH REGISTRY...", "PROTOCOL: ONLINE"]`). Use `gsap.timeline()` with staggered `opacity: 0 → 1` tweens, each line appearing ~200ms apart. After the last line fades in, pause 400ms, then `opacity → 0` the whole block.

2. **Model reveal** — The Three.js `pivot` starts at `scale.setScalar(0)` and `pivot.rotation.y = Math.PI * 2`. Drive the scale and rotation with `gsap.to(pivot.scale, { x: targetScale, y: targetScale, z: targetScale, duration: 1.2, ease: "power3.out" })` and `gsap.to(pivot.rotation, { y: 0, duration: 1.2, ease: "power3.out" })` in parallel. This is the GSAP-driven 3D animation that CSS cannot do.

3. **Glow punch on arrival** — When the timeline reaches the model's `onComplete`, fire a `gsap.timeline()` that tweens the Three.js `rim` light's intensity from `0.8 → 3.5 → 0.8` over ~0.6s. The armor literally flashes brighter for a split second. This requires passing the `THREE.DirectionalLight` object to the GSAP tween: `gsap.to(rimLight, { intensity: 3.5, duration: 0.2, yoyo: true, repeat: 1 })`.

4. **Idle breathing** — After the reveal, a looping GSAP tween very slowly pulses the model's Y position (`pivot.position.y`) ±0.04 units over 3s (`yoyo: true, repeat: -1, ease: "sine.inOut"`). Combined with the background's floating dots, the whole page feels alive without being distracting.

---

### Section B — Scroll-Triggered Transformation Story

**The idea:** The page becomes taller. The focus is the Squirt model. There is a full transition between the first hero section to this, like a reset in what the user saw. As the user scrolls down, GSAP ScrollTrigger links the scroll position to a cross-fade (Squirt opacity goes to 0, Armored Titan opacity rises to 1), stat counters count up, and feature bullets slide in. The page tells a story of progression.

**Why it works:** Scroll-driven storytelling is one of the most proven techniques for landing page engagement. Apple uses it for every product page. Stripe uses it. The narrative structure — "here's where you are, here's where you'll be, here's how it works" — does the sales pitch work so the CTA at the bottom feels earned.

**Implementation breakdown:**

1. **GSAP ScrollTrigger plugin** — Install or import from the GSAP bundle. Create a `ScrollTrigger` that `pin`s the hero section and uses `scrub: true` to link scroll progress to a timeline. `scrub: 1` adds a 1-second lag for smoothness.

2. **Model crossfade** — The two canvases are stacked with `position: absolute` inside a shared container. The timeline tweens `opacitySquirt` from `1 → 0` and `opacityGiant` from `0 → 1` as scroll progresses from 0% to 50% through the pinned section.

3. **Three.js rotation on scroll** — Instead of auto-rotating, the `pivot.rotation.y` is driven by scroll progress. In the ScrollTrigger `onUpdate` callback: `pivot.rotation.y = progress * Math.PI * 2`. The turtle literally spins as you scroll — purposeful, discoverable, satisfying.

4. **Stat counters** — A section below the hero has numbers: `247 coaches`, `4,800+ sessions`. GSAP's `gsap.to(obj, { val: 247, onUpdate: () => (el.textContent = Math.round(obj.val).toString()) })` tweens a plain JS object's property and renders the value each frame. Trigger these with a `ScrollTrigger` with `start: "top 80%"`.

5. **Feature rows** — Three `.panel-accent` rows (`Find a coach`, `Book a session`, `Grow together`) each animate in from `x: -40, opacity: 0` as they enter the viewport, staggered.

---

### Section C — Rotating Headline (Typewriter)

**The idea:** The big heading `Turtle Coaching` stays, but below it a single line cycles through promises: `> FIND YOUR COACH.`, then it deletes character by character, then types `> BOOK YOUR SESSION.`, then `> BECOME UNSTOPPABLE.`. Loops indefinitely.

**Why it works:** It's extremely quick to implement and adds motion to the hero without changing the layout. The typewriter effect is a classic for a reason — it creates forward momentum and communicates multiple value props sequentially without cluttering the screen. In a cyberpunk monospace font it looks native, not gimmicky.

**Implementation breakdown:**

1. GSAP has a `TextPlugin` (bundled, just needs registration: `gsap.registerPlugin(TextPlugin)`). It tweens `element.textContent` character by character.

2. Build a looping timeline:
   ```
   const lines = ['> FIND YOUR COACH.', '> BOOK YOUR SESSION.', '> BECOME UNSTOPPABLE.'];
   const tl = gsap.timeline({ repeat: -1 });
   for (const line of lines) {
     tl.to(el, { duration: line.length * 0.05, text: line, ease: 'none' })
       .to({}, { duration: 1.5 }) // hold
       .to(el, { duration: 0.4, text: '', ease: 'none' }) // delete
       .to({}, { duration: 0.3 }); // pause between
   }
   ```

3. The element should use the `.mono` class and match the `section-label` style. Add a blinking cursor pseudo-element via a scoped CSS `::after` rule (just `content: '_'; animation: blink 1s step-end infinite`).

---

### Section D — Magnetic Hover on CTA Buttons

**The idea:** The "Browse Coaches" and "Sign in" buttons subtly follow the cursor when it's nearby — not clicking through, just a slight magnetic lean (±8px max). When the cursor leaves, they spring back.

**Why it works:** It's an unexpected moment of delight that doesn't distract from the message. Users notice it and it makes the CTA feel *alive*. This technique is used by agencies like Awwwards winners to signal craft quality.

**Implementation breakdown:**

1. In `onMount`, attach a `mousemove` listener to the page. For each CTA button, compute the distance between the cursor and the button's center using `getBoundingClientRect()`.

2. If the cursor is within a threshold (e.g., 80px), calculate the offset as a fraction of the distance: `dx = (mouseX - centerX) * 0.25`. Use `gsap.to(btn, { x: dx, y: dy, duration: 0.4, ease: "power2.out" })`.

3. On `mouseleave` of the container, spring back: `gsap.to(btn, { x: 0, y: 0, duration: 0.6, ease: "elastic.out(1, 0.4)" })`. The elastic ease is what makes it feel spring-loaded rather than mechanical.

4. This works on `<a>` elements directly — GSAP can tween any DOM element's transform.

---

### Section E — Light Tracking (Cursor-Driven Rim Light)

**The idea:** Instead of drag-to-spin, the cursor position controls the Three.js directional (rim) light's position. Move the mouse left: the blue armor gleam shifts left. Move right: it shifts right. The turtle stays at a fixed angle; only the *light* changes.

**Why it works:** It gives the impression of 3D depth and interactivity without the "toy" connotation of dragging. The model feels like it's *reacting* to your presence, not that you're playing with a spinner. This is closer to how luxury product pages (e.g., Apple Watch) treat 3D visuals.

**Implementation breakdown:**

1. Listen for `mousemove` on the canvas (or the whole page). Normalize mouse coordinates: `normX = (e.clientX / window.innerWidth) * 2 - 1`, same for Y.

2. Each frame, update the rim light's position: `rim.position.set(normX * 5, normY * -3, 2)`. No GSAP needed for this — it happens every RAF frame in the render loop.

3. Optionally, lerp the light position for smoothness: `rim.position.x += (targetX - rim.position.x) * 0.08` per frame (manual lerp, no GSAP). This prevents snapping.

4. Remove the `mouseenter`/`mouseleave` auto-rotate logic entirely. The model can still very slowly auto-rotate (0.002 per frame instead of 0.006) as the idle state.

---

## Recommended Combination

For the highest impact with achievable effort:

| Layer | Technique | Effort |
|-------|-----------|--------|
| **Hero entrance** | Section A (Boot sequence + single model reveal) | Medium |
| **Headline** | Section C (Typewriter cycling tagline) | Low |
| **Model interaction** | Section E (Cursor-driven rim light) | Low |
| **CTA buttons** | Section D (Magnetic hover) | Low |
| **Bonus (if time)** | Section B stat counters below the fold | Medium |

This stack makes every element on the page feel alive in a purposeful, cyberpunk-appropriate way. The GSAP timeline is doing real work — coordinating a cinematic sequence, not just fading divs in.

---

## References (Techniques, Not URLs)

- **GSAP Timeline + TextPlugin typewriter**: documented in GSAP's official guides under "TextPlugin" — register it, then tween `text` property.
- **GSAP ScrollTrigger pin + scrub**: GSAP's ScrollTrigger docs cover `pin`, `scrub`, and `onUpdate` for scroll-linked 3D.
- **Magnetic button technique**: popularized by Bruno Simon (threejs-journey.com designer), widely documented in codepen demos searching "gsap magnetic button".
- **Three.js light as cursor reaction**: a common technique in product configurator demos — search "three.js cursor light" for patterns.
- **Cinematic boot sequence**: inspired by retro terminal aesthetics used in games like SOMA and Alien: Isolation's UI, and many cyberpunk game intros.
