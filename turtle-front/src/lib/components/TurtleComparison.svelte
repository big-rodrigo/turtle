<script lang="ts">
	import { onMount } from 'svelte';
	import * as THREE from 'three';
	import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

	let canvasSquirt: HTMLCanvasElement;
	let canvasGiant: HTMLCanvasElement;
	let opacitySquirt = $state(0);
	let opacityGiant = $state(0);

	const CANVAS_W = 320;
	const CANVAS_H = 340;

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

	function setupScene(canvas: HTMLCanvasElement, modelPath: string, onLoaded: () => void) {
		const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
		renderer.setSize(CANVAS_W, CANVAS_H);
		renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
		renderer.outputColorSpace = THREE.SRGBColorSpace;
		renderer.toneMapping = THREE.ACESFilmicToneMapping;
		renderer.toneMappingExposure = 1.2;

		const scene = new THREE.Scene();
		const camera = new THREE.PerspectiveCamera(45, CANVAS_W / CANVAS_H, 0.01, 1000);
		camera.position.set(0, 0, 3);

		// Cyberpunk lighting
		const ambient = new THREE.AmbientLight(0x1a3ecf, 0.5);
		scene.add(ambient);
		const key = new THREE.DirectionalLight(0xe8edfc, 1.4);
		key.position.set(2, 3, 4);
		scene.add(key);
		const rim = new THREE.DirectionalLight(0x1a3ecf, 0.8);
		rim.position.set(-3, 1, -3);
		scene.add(rim);
		const fill = new THREE.DirectionalLight(0x4060ff, 0.3);
		fill.position.set(0, -2, 2);
		scene.add(fill);

		const pivot = new THREE.Group();
		scene.add(pivot);

		let loadedScene: THREE.Object3D | null = null;

		const loader = new GLTFLoader();
		loader.load(modelPath, (gltf) => {
			// Compute bounding box before any transform changes
			const box = new THREE.Box3().setFromObject(gltf.scene);
			const center = box.getCenter(new THREE.Vector3());
			const size = box.getSize(new THREE.Vector3());
			const maxDim = Math.max(size.x, size.y, size.z);

			// Center the model at origin (in its own space)
			gltf.scene.position.sub(center);

			// Scale the pivot (not the scene) to preserve centering math
			pivot.add(gltf.scene);
			pivot.scale.setScalar(2 / maxDim);
			loadedScene = gltf.scene;
			onLoaded();
		});

		let autoRotate = true;
		let dragging = false;
		let lastX = 0;
		let raf: number;

		function animate() {
			raf = requestAnimationFrame(animate);
			if (autoRotate && !dragging) pivot.rotation.y += 0.006;
			renderer.render(scene, camera);
		}
		animate();

		function onMouseEnter() { autoRotate = false; }
		function onMouseLeave() { autoRotate = true; dragging = false; }
		function onPointerDown(e: PointerEvent) {
			dragging = true;
			lastX = e.clientX;
			canvas.setPointerCapture(e.pointerId);
		}
		function onPointerMove(e: PointerEvent) {
			if (!dragging) return;
			pivot.rotation.y += (e.clientX - lastX) * 0.01;
			lastX = e.clientX;
		}
		function onPointerUp() { dragging = false; }

		canvas.addEventListener('mouseenter', onMouseEnter);
		canvas.addEventListener('mouseleave', onMouseLeave);
		canvas.addEventListener('pointerdown', onPointerDown);
		canvas.addEventListener('pointermove', onPointerMove);
		canvas.addEventListener('pointerup', onPointerUp);

		return () => {
			cancelAnimationFrame(raf);
			canvas.removeEventListener('mouseenter', onMouseEnter);
			canvas.removeEventListener('mouseleave', onMouseLeave);
			canvas.removeEventListener('pointerdown', onPointerDown);
			canvas.removeEventListener('pointermove', onPointerMove);
			canvas.removeEventListener('pointerup', onPointerUp);
			if (loadedScene) disposeGltfScene(loadedScene);
			renderer.dispose();
		};
	}

	onMount(() => {
		const cleanupSquirt = setupScene(canvasSquirt, '/models/squirt/scene.gltf', () => { opacitySquirt = 1; });
		const cleanupGiant = setupScene(canvasGiant, '/models/giant-turtle/scene.gltf', () => { opacityGiant = 1; });
		return () => {
			cleanupSquirt();
			cleanupGiant();
		};
	});
</script>

<div class="flex gap-8 items-center justify-center flex-wrap">
	<!-- Squirt panel -->
	<div class="flex flex-col items-center gap-[10px]">
		<p class="mono text-[1rem] tracking-[0.2em] text-primary m-0 text-shadow-primary-sm">
			FROM THIS
		</p>
		<div class="panel p-0 overflow-hidden leading-none">
			<canvas bind:this={canvasSquirt} width={CANVAS_W} height={CANVAS_H} style:opacity={opacitySquirt} class="block transition-opacity duration-700 ease-in-out"></canvas>
		</div>
		<div class="text-center">
			<p class="mono text-[0.85rem] font-bold tracking-[0.12em] text-text-primary mb-0.5">
				SQUIRT
			</p>
			<p class="mono text-[0.6rem] tracking-[0.15em] text-text-muted">
				WHERE YOU START
			</p>
		</div>
	</div>

	<!-- Arrow -->
	<div class="flex flex-col items-center gap-2 pb-10">
		<span class="mono text-[2rem] text-primary text-glow-primary leading-none">→</span>
		<p class="mono text-[0.7rem] tracking-[0.18em] text-text-muted">EVOLVE</p>
	</div>

	<!-- Giant turtle panel -->
	<div class="flex flex-col items-center gap-[10px]">
		<p class="mono text-[1rem] tracking-[0.2em] text-primary m-0 text-shadow-primary-sm">
			TO THIS
		</p>
		<div class="panel p-0 overflow-hidden leading-none">
			<canvas bind:this={canvasGiant} width={CANVAS_W} height={CANVAS_H} style:opacity={opacityGiant} class="block transition-opacity duration-700 ease-in-out"></canvas>
		</div>
		<div class="text-center">
			<p class="mono text-[0.85rem] font-bold tracking-[0.12em] text-text-primary mb-0.5">
				ARMORED TITAN
			</p>
			<p class="mono text-[0.6rem] tracking-[0.15em] text-text-muted">
				WHERE YOU END UP
			</p>
		</div>
	</div>
</div>
