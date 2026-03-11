<script lang="ts">
  import { onMount } from 'svelte';
  import * as THREE from 'three';

  let canvas: HTMLCanvasElement;

  onMount(() => {
    let w = window.innerWidth;
    let h = window.innerHeight;

    // Renderer
    const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(w, h);
    renderer.setClearColor(0x0d0d0d, 1);

    // Scene & camera (orthographic — 2D plane)
    const scene = new THREE.Scene();
    const camera = new THREE.OrthographicCamera(-w / 2, w / 2, h / 2, -h / 2, 0.1, 100);
    camera.position.z = 10;

    // ── Config ───────────────────────────────────────────────────────────────
    const CELL = 60;          // grid cell size in px
    const JITTER = 15;        // random offset from grid node
    const CONNECT_DIST = 105; // connect points within this distance
    const SPEED = 0.25;       // base drift speed
    const SPRING = 0.008;     // restoring force toward home position
    const DAMPING = 0.90;     // velocity damping per frame
    const MOUSE_RADIUS = 100;
    const MOUSE_STRENGTH = 0.7;

    // ── Build jittered grid ──────────────────────────────────────────────────
    type Point = { x: number; y: number; hx: number; hy: number; vx: number; vy: number };

    function buildPoints(width: number, height: number): Point[] {
      const pts: Point[] = [];
      const cols = Math.ceil(width / CELL) + 1;
      const rows = Math.ceil(height / CELL) + 1;
      // offset so grid is centered
      const ox = -width / 2 - CELL / 2;
      const oy = -height / 2 - CELL / 2;
      for (let r = 0; r <= rows; r++) {
        for (let c = 0; c <= cols; c++) {
          const hx = ox + c * CELL + (Math.random() * 2 - 1) * JITTER;
          const hy = oy + r * CELL + (Math.random() * 2 - 1) * JITTER;
          pts.push({
            x: hx,
            y: hy,
            hx,
            hy,
            vx: (Math.random() * 2 - 1) * SPEED,
            vy: (Math.random() * 2 - 1) * SPEED,
          });
        }
      }
      return pts;
    }

    let points = buildPoints(w, h);

    // ── Material ─────────────────────────────────────────────────────────────
    const lineMat = new THREE.LineBasicMaterial({
      color: 0x1a3ecf,
      transparent: true,
      opacity: 0.22,
    });

    // Pre-allocate a persistent buffer large enough for all possible segments.
    // Max segments = n*(n-1)/2. With ~300 points worst case, that's ~45000 segs.
    // In practice ~120 points → ~7140 segs. 8000 is a safe upper bound.
    const MAX_SEGS = 8000;
    const posArr = new Float32Array(MAX_SEGS * 6); // 2 vertices × 3 floats each
    const geo = new THREE.BufferGeometry();
    const posAttr = new THREE.BufferAttribute(posArr, 3);
    posAttr.setUsage(THREE.DynamicDrawUsage);
    geo.setAttribute('position', posAttr);
    const lineObj = new THREE.LineSegments(geo, lineMat);
    scene.add(lineObj);

    // ── Mouse tracking ───────────────────────────────────────────────────────
    let mouseX = 99999;
    let mouseY = 99999;

    function onMouseMove(e: MouseEvent) {
      mouseX = e.clientX - w / 2;
      mouseY = -(e.clientY - h / 2);
    }

    window.addEventListener('mousemove', onMouseMove);

    // ── Animation loop ───────────────────────────────────────────────────────
    let raf: number;

    function updateLines() {
      let idx = 0;
      const n = points.length;
      const distSqThresh = CONNECT_DIST * CONNECT_DIST;
      for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
          const dx = points[i].x - points[j].x;
          const dy = points[i].y - points[j].y;
          if (dx * dx + dy * dy < distSqThresh) {
            posArr[idx++] = points[i].x; posArr[idx++] = points[i].y; posArr[idx++] = 0;
            posArr[idx++] = points[j].x; posArr[idx++] = points[j].y; posArr[idx++] = 0;
          }
        }
      }
      geo.setDrawRange(0, idx / 3);
      posAttr.needsUpdate = true;
    }

    function tick() {
      raf = requestAnimationFrame(tick);

      for (const p of points) {
        // Spring toward home position
        p.vx += (p.hx - p.x) * SPRING;
        p.vy += (p.hy - p.y) * SPRING;

        // Mouse repulsion
        const dx = p.x - mouseX;
        const dy = p.y - mouseY;
        const distSq = dx * dx + dy * dy;
        if (distSq < MOUSE_RADIUS * MOUSE_RADIUS && distSq > 0.01) {
          const dist = Math.sqrt(distSq);
          const force = (MOUSE_RADIUS - dist) / MOUSE_RADIUS;
          p.vx += (dx / dist) * force * MOUSE_STRENGTH;
          p.vy += (dy / dist) * force * MOUSE_STRENGTH;
        }

        // Damping
        p.vx *= DAMPING;
        p.vy *= DAMPING;

        p.x += p.vx;
        p.y += p.vy;

        // Soft boundary bounce (stay within screen + small margin)
        const margin = 10;
        const bx = w / 2 + margin;
        const by = h / 2 + margin;
        if (p.x < -bx) { p.x = -bx; p.vx = Math.abs(p.vx); }
        if (p.x > bx)  { p.x = bx;  p.vx = -Math.abs(p.vx); }
        if (p.y < -by) { p.y = -by; p.vy = Math.abs(p.vy); }
        if (p.y > by)  { p.y = by;  p.vy = -Math.abs(p.vy); }
      }

      updateLines();
      renderer.render(scene, camera);
    }

    tick();

    // ── Resize handler ───────────────────────────────────────────────────────
    function onResize() {
      w = window.innerWidth;
      h = window.innerHeight;
      renderer.setSize(w, h);
      camera.left = -w / 2;
      camera.right = w / 2;
      camera.top = h / 2;
      camera.bottom = -h / 2;
      camera.updateProjectionMatrix();
      points = buildPoints(w, h);
    }

    window.addEventListener('resize', onResize);

    // ── Cleanup ──────────────────────────────────────────────────────────────
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('resize', onResize);
      geo.dispose();
      lineMat.dispose();
      renderer.dispose();
    };
  });
</script>

<canvas bind:this={canvas} class="fixed inset-0 z-0 w-full h-full block"></canvas>
