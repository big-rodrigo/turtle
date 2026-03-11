<script lang="ts">
	import { onMount } from 'svelte';
	import * as THREE from 'three';
	import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
	import gsap from 'gsap';
	import { ScrollTrigger } from 'gsap/ScrollTrigger';

	const BOOT_LINES = [
		'// TURTLE COACHING OS v1.0.0',
		'LOADING COACH REGISTRY...',
		'SYNCING SESSION DATA...',
		'PROTOCOL: ONLINE'
	];

	let heroSection: HTMLElement;
	let canvasEl: HTMLCanvasElement;
	let bootContainer: HTMLElement;
	let phase1El: HTMLElement;
	let phase2El: HTMLElement;
	let phase3El: HTMLElement;
	let ctaBrowse: HTMLElement;
	let ctaSignIn: HTMLElement;

	function smoothstep(x: number, edge0: number, edge1: number): number {
		const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
		return t * t * (3 - 2 * t);
	}

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

	onMount(() => {
		// ── Three.js Setup ──────────────────────────────────
		const renderer = new THREE.WebGLRenderer({ canvas: canvasEl, antialias: true, alpha: true });
		renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
		renderer.setSize(window.innerWidth, window.innerHeight);
		renderer.outputColorSpace = THREE.SRGBColorSpace;
		renderer.toneMapping = THREE.ACESFilmicToneMapping;
		renderer.toneMappingExposure = 1.2;

		const scene = new THREE.Scene();
		const camera = new THREE.PerspectiveCamera(
			45,
			window.innerWidth / window.innerHeight,
			0.01,
			1000
		);
		camera.position.set(0, 0, 3);

		// Cyberpunk lighting
		const ambient = new THREE.AmbientLight(0x1a3ecf, 0.5);
		scene.add(ambient);
		const keyLight = new THREE.DirectionalLight(0xe8edfc, 1.4);
		keyLight.position.set(2, 3, 4);
		scene.add(keyLight);
		const rim = new THREE.DirectionalLight(0x1a3ecf, 0.8);
		rim.position.set(-3, 1, -2);
		scene.add(rim);
		const fill = new THREE.DirectionalLight(0x4060ff, 0.3);
		fill.position.set(0, -2, 2);
		scene.add(fill);

		// Shared rotation group + individual pivots
		const rotationGroup = new THREE.Group();
		scene.add(rotationGroup);

		const squirtPivot = new THREE.Group();
		squirtPivot.scale.setScalar(0);
		squirtPivot.rotation.y = Math.PI * 2;
		rotationGroup.add(squirtPivot);

		const giantPivot = new THREE.Group();
		giantPivot.visible = false;
		rotationGroup.add(giantPivot);

		// Material caches for crossfade
		const squirtMaterials: THREE.Material[] = [];
		const giantMaterials: THREE.Material[] = [];
		let squirtScene: THREE.Object3D | null = null;
		let giantScene: THREE.Object3D | null = null;
		let squirtTargetScale = 1;
		let squirtReady = false;
		let bootDone = false;
		let bootComplete = false;
		let scrollProgress = 0;

		// Rim light cursor targets
		let rimTargetX = -3;
		let rimTargetY = 1;

		// GSAP refs
		let bootTl: gsap.core.Timeline | null = null;
		let revealTl: gsap.core.Timeline | null = null;
		let breathingTween: gsap.core.Tween | null = null;
		let st: ScrollTrigger | null = null;

		// ── GLTF Loading ────────────────────────────────────
		const loader = new GLTFLoader();

		function cacheMaterials(root: THREE.Object3D, cache: THREE.Material[]) {
			root.traverse((child: any) => {
				if (child.isMesh && child.material) {
					const mats = Array.isArray(child.material) ? child.material : [child.material];
					for (const mat of mats) {
						mat.transparent = true;
						mat.depthWrite = true;
						cache.push(mat);
					}
				}
			});
		}

		// Load Squirt
		loader.load('/models/squirt/scene.gltf', (gltf) => {
			const box = new THREE.Box3().setFromObject(gltf.scene);
			const center = box.getCenter(new THREE.Vector3());
			const size = box.getSize(new THREE.Vector3());
			const maxDim = Math.max(size.x, size.y, size.z);
			gltf.scene.position.sub(center);
			squirtPivot.add(gltf.scene);
			squirtTargetScale = (1 / maxDim);
			squirtScene = gltf.scene;
			cacheMaterials(gltf.scene, squirtMaterials);
			squirtReady = true;
			if (bootDone) revealSquirt();
		});

		// Load Giant Turtle
		loader.load('/models/giant-turtle/scene.gltf', (gltf) => {
			const box = new THREE.Box3().setFromObject(gltf.scene);
			const center = box.getCenter(new THREE.Vector3());
			const size = box.getSize(new THREE.Vector3());
			const maxDim = Math.max(size.x, size.y, size.z);
			gltf.scene.position.sub(center);
			giantPivot.add(gltf.scene);
			giantPivot.scale.setScalar(2 / maxDim);
			giantScene = gltf.scene;
			cacheMaterials(gltf.scene, giantMaterials);
			for (const m of giantMaterials) m.opacity = 0;
		});

		// ── Boot Sequence ───────────────────────────────────
		function runBoot() {
			const els = Array.from(bootContainer.querySelectorAll('p'));
			bootTl = gsap.timeline({
				onComplete: () => {
					bootDone = true;
					if (squirtReady) revealSquirt();
				}
			});
			els.forEach((el, i) => {
				bootTl!.to(el, { opacity: 1, duration: 0.15, ease: 'none' }, i * 0.22);
			});
			bootTl.to({}, { duration: 0.4 });
			bootTl.to(bootContainer, { opacity: 0, duration: 0.3, ease: 'power2.in' });
		}

		function revealSquirt() {
			revealTl = gsap.timeline({
				onComplete: () => {
					bootComplete = true;
					// Read current scroll progress from ScrollTrigger
					if (st) scrollProgress = st.progress;
					// Start breathing (killed when user scrolls)
					breathingTween = gsap.to(squirtPivot.position, {
						y: 0.04,
						duration: 3,
						yoyo: true,
						repeat: -1,
						ease: 'sine.inOut'
					});
				}
			});
			revealTl.to(
				squirtPivot.scale,
				{
					x: squirtTargetScale,
					y: squirtTargetScale,
					z: squirtTargetScale,
					duration: 1.2,
					ease: 'power3.out'
				},
				0
			);
			revealTl.to(squirtPivot.rotation, { y: 0, duration: 1.2, ease: 'power3.out' }, 0);
			revealTl.to(
				rim,
				{ intensity: 3.5, duration: 0.2, yoyo: true, repeat: 1, ease: 'power2.out' },
				0.9
			);
		}

		const startTimer = setTimeout(runBoot, 80);

		// Entrance animation for phase1 text
		gsap.from(phase1El, { y: 20, opacity: 0, duration: 0.4, ease: 'power2.out' });

		// ── ScrollTrigger (pin immediately) ──────────────────
		st = ScrollTrigger.create({
			trigger: heroSection,
			start: 'top top',
			end: '+=3000',
			pin: true,
			scrub: 1,
			pinSpacing: true,
			onUpdate: (self) => {
				scrollProgress = self.progress;
			}
		});

		// ── Magnetic Buttons ────────────────────────────────
		const attractedMap = new WeakMap<HTMLElement, boolean>();
		function applyMagnetic(btn: HTMLElement, mx: number, my: number) {
			if (!btn) return;
			const rect = btn.getBoundingClientRect();
			const cx = rect.left + rect.width / 2;
			const cy = rect.top + rect.height / 2;
			const dx = mx - cx;
			const dy = my - cy;
			const dist = Math.sqrt(dx * dx + dy * dy);
			if (dist < 80) {
				attractedMap.set(btn, true);
				gsap.to(btn, { x: dx * 0.25, y: dy * 0.25, duration: 0.4, ease: 'power2.out' });
			} else if (attractedMap.get(btn)) {
				attractedMap.set(btn, false);
				gsap.to(btn, { x: 0, y: 0, duration: 0.6, ease: 'elastic.out(1, 0.4)' });
			}
		}

		// ── Event Listeners ─────────────────────────────────
		function onMouseMove(e: MouseEvent) {
			const normX = (e.clientX / window.innerWidth) * 2 - 1;
			const normY = (e.clientY / window.innerHeight) * 2 - 1;
			rimTargetX = normX * 5;
			rimTargetY = normY * -3;
			applyMagnetic(ctaBrowse, e.clientX, e.clientY);
			applyMagnetic(ctaSignIn, e.clientX, e.clientY);
		}
		window.addEventListener('mousemove', onMouseMove);

		function onResize() {
			const w = window.innerWidth;
			const h = window.innerHeight;
			renderer.setSize(w, h);
			camera.aspect = w / h;
			camera.updateProjectionMatrix();
		}
		window.addEventListener('resize', onResize);

		// ── Animation Loop ──────────────────────────────────
		let raf: number;

		function tick() {
			raf = requestAnimationFrame(tick);

			// Cursor-driven rim light (always active)
			rim.position.x += (rimTargetX - rim.position.x) * 0.08;
			rim.position.y += (rimTargetY - rim.position.y) * 0.08;

			if (bootComplete) {
				// Kill breathing when user starts scrolling
				if (scrollProgress > 0.01 && breathingTween) {
					breathingTween.kill();
					breathingTween = null;
					gsap.to(squirtPivot.position, { y: 0, duration: 0.3, ease: 'power2.out' });
				}

				// Scroll-driven rotation
				rotationGroup.rotation.y = scrollProgress * Math.PI * 2;

				// Crossfade opacities
				const sOp = 1 - smoothstep(scrollProgress, 0.2, 0.5);
				const gOp = smoothstep(scrollProgress, 0.3, 0.55);

				for (const m of squirtMaterials) m.opacity = sOp;
				squirtPivot.visible = sOp > 0.01;

				for (const m of giantMaterials) m.opacity = gOp;
				giantPivot.visible = gOp > 0.01;

				// Rim light intensifies during morph
				const morphGlow =
					smoothstep(scrollProgress, 0.25, 0.4) - smoothstep(scrollProgress, 0.45, 0.6);
				rim.intensity = 0.8 + morphGlow * 3.0;

				// Text overlay opacities (direct DOM for perf — same pattern as GSAP)
				const p1 = 1 - smoothstep(scrollProgress, 0.15, 0.22);
				const p2 =
					smoothstep(scrollProgress, 0.2, 0.28) *
					(1 - smoothstep(scrollProgress, 0.5, 0.58));
				const p3 = smoothstep(scrollProgress, 0.55, 0.65);
				const ctaVisible = scrollProgress > 0.85;

				phase1El.style.opacity = p1.toString();
				phase2El.style.opacity = p2.toString();
				phase3El.style.opacity = p3.toString();
				phase3El.style.pointerEvents = ctaVisible ? 'auto' : 'none';
			}

			renderer.render(scene, camera);
		}
		tick();

		// ── Cleanup ─────────────────────────────────────────
		return () => {
			clearTimeout(startTimer);
			cancelAnimationFrame(raf);
			window.removeEventListener('mousemove', onMouseMove);
			window.removeEventListener('resize', onResize);
			st?.kill();
			bootTl?.kill();
			revealTl?.kill();
			breathingTween?.kill();
			gsap.killTweensOf(squirtPivot.scale);
			gsap.killTweensOf(squirtPivot.rotation);
			gsap.killTweensOf(squirtPivot.position);
			gsap.killTweensOf(rim);
			gsap.killTweensOf(ctaBrowse);
			gsap.killTweensOf(ctaSignIn);
			if (squirtScene) disposeGltfScene(squirtScene);
			if (giantScene) disposeGltfScene(giantScene);
			renderer.dispose();
		};
	});
</script>

<section bind:this={heroSection} class="relative h-screen w-full overflow-hidden">
	<!-- Boot sequence overlay -->
	<div
		bind:this={bootContainer}
		class="absolute inset-0 z-20 flex flex-col items-start justify-center gap-[6px] pl-8 pointer-events-none"
	>
		{#each BOOT_LINES as line}
			<p class="mono m-0 leading-relaxed text-[0.72rem] tracking-[0.1em] text-primary opacity-0">
				{line}
			</p>
		{/each}
	</div>

	<!-- Three.js canvas -->
	<canvas bind:this={canvasEl} class="absolute top-0 left-0 z-0 block"></canvas>

	<!-- Phase 1: Origin (Squirt) -->
	<div
		bind:this={phase1El}
		class="absolute inset-0 z-10 flex flex-col items-center justify-between pt-[72px] pb-16 pointer-events-none"
	>
		<div class="text-center">
			<p class="section-label mb-2 text-[0.7rem] tracking-[0.2em] text-shadow-primary-md">
				ORIGIN // STATUS
			</p>
			<h1 class="page-title-lg mb-3">
				Turtle<br /><span class="text-primary text-shadow-primary-lg">Coaching</span>
			</h1>
		</div>
		<div class="text-center">
			<p class="mono text-[0.85rem] font-bold tracking-[0.12em] text-text-primary mb-0.5">
				SQUIRT
			</p>
			<p class="mono text-[0.6rem] tracking-[0.15em] text-text-muted">
				EVERY EXPERT WAS ONCE A BEGINNER
			</p>
		</div>
	</div>

	<!-- Phase 2: Transformation -->
	<div
		bind:this={phase2El}
		class="absolute inset-0 z-10 flex items-center justify-center pointer-events-none"
		style:opacity="0"
	>
		<div class="text-center">
			<p class="section-label mb-2 text-[0.7rem] tracking-[0.2em] text-shadow-primary-md">
				TRANSFORMATION // IN PROGRESS
			</p>
			<p
				class="glitch-text mono text-[1.5rem] font-bold tracking-[0.15em] text-primary text-shadow-primary-lg"
			>
				EVOLVING...
			</p>
		</div>
	</div>

	<!-- Phase 3: Destination (Giant Turtle) -->
	<div
		bind:this={phase3El}
		class="absolute inset-0 z-10 flex flex-col items-center justify-between pt-[72px] pb-16 pointer-events-none"
		style:opacity="0"
	>
		<div class="text-center">
			<p class="section-label mb-2 text-[0.7rem] tracking-[0.2em] text-shadow-primary-md">
				DESTINATION // STATUS
			</p>
			<h2 class="page-title-lg mb-3">
				This is where<br /><span class="text-primary text-shadow-primary-lg">you'll be</span>
			</h2>
		</div>
		<div class="text-center">
			<p class="mono text-[0.85rem] font-bold tracking-[0.12em] text-text-primary mb-3">
				ARMORED TITAN
			</p>
			<div class="flex gap-3 justify-center">
				<a bind:this={ctaBrowse} href="/coaches" class="btn btn-primary btn-lg">Browse Coaches</a
				>
				<a bind:this={ctaSignIn} href="/login" class="btn btn-secondary btn-lg">Sign in</a>
			</div>
		</div>
	</div>
</section>

<style>
	.glitch-text {
		animation: glitch 0.3s infinite;
	}

	@keyframes glitch {
		0% {
			transform: translate(0);
		}
		20% {
			transform: translate(-2px, 1px);
		}
		40% {
			transform: translate(2px, -1px);
		}
		60% {
			transform: translate(-1px, -1px);
		}
		80% {
			transform: translate(1px, 1px);
		}
		100% {
			transform: translate(0);
		}
	}
</style>
