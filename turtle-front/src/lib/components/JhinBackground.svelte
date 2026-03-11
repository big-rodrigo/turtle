<script lang="ts">
	import { onMount } from 'svelte';
	import * as THREE from 'three';
	import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

	let { delay = 0 }: { delay?: number } = $props();

	let canvas: HTMLCanvasElement;
	let opacity = $state(0);

	onMount(() => {
		let destroyed = false;
		let raf: number;
		let renderer: THREE.WebGLRenderer | null = null;
		let mixer: THREE.AnimationMixer | null = null;
		let loadedScene: THREE.Object3D | null = null;
		let onResize: (() => void) | null = null;
		let onPointerDown: ((e: PointerEvent) => void) | null = null;
		let onPointerMove: ((e: PointerEvent) => void) | null = null;
		let onPointerUp: ((e: PointerEvent) => void) | null = null;

		const cancelDelay = setTimeout(() => {
			if (destroyed) return;
			init();
		}, delay);

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

		function init() {
			let w = window.innerWidth;
			let h = window.innerHeight;

			renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
			renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
			renderer.setSize(w, h);
			renderer.outputColorSpace = THREE.SRGBColorSpace;
			renderer.toneMapping = THREE.ACESFilmicToneMapping;
			renderer.toneMappingExposure = 1.1;

			const scene = new THREE.Scene();
			const camera = new THREE.PerspectiveCamera(45, w / h, 0.01, 1000);
			camera.position.set(1.5, 1.0, 5);
			camera.lookAt(0.5, 0, 0);

			scene.add(new THREE.AmbientLight(0x1a3ecf, 0.4));
			const key = new THREE.DirectionalLight(0xe8edfc, 1.2);
			key.position.set(2, 4, 5);
			scene.add(key);
			const rim = new THREE.DirectionalLight(0x1a3ecf, 1.2);
			rim.position.set(-4, 2, -3);
			scene.add(rim);
			const fill = new THREE.DirectionalLight(0x4060ff, 0.3);
			fill.position.set(0, -2, 2);
			scene.add(fill);

			const pivot = new THREE.Group();
			pivot.position.set(-3.2, 0.6, 0);
			pivot.rotation.y = Math.PI / 2.5;
			scene.add(pivot);

			const ANIMS = [
				{ loop: 'jhin_spell4_idle.anm' },
				{ loop: 'jhin_idle01.anm' },
				{ in: 'jhin_dance_in.anm', loop: 'jhin_dance_loop.anm' },
				{ loop: 'jhin_joke.anm' },
				{ loop: 'jhin_laugh.anm' },
				{ loop: 'jhin_taunt.anm' },
				{ loop: 'jhin_recall.anm' },
			];
			let animIdx = 0;
			let animations: THREE.AnimationClip[] = [];

			function playAnim(entry: { in?: string; loop: string }) {
				if (!mixer || !animations.length) return;
				mixer.stopAllAction();
				const clipLoop = THREE.AnimationClip.findByName(animations, entry.loop);
				if (!clipLoop) return;
				if (entry.in) {
					const clipIn = THREE.AnimationClip.findByName(animations, entry.in);
					if (clipIn) {
						const actionIn = mixer.clipAction(clipIn);
						actionIn.setLoop(THREE.LoopOnce, 1);
						actionIn.clampWhenFinished = true;
						actionIn.reset().play();
						function onFinished(e: any) {
							if (e.action === actionIn) {
								mixer!.removeEventListener('finished', onFinished);
								const actionLoop = mixer!.clipAction(clipLoop!);
								actionLoop.setLoop(THREE.LoopRepeat, Infinity);
								actionIn.crossFadeTo(actionLoop, 0.3, false);
								actionLoop.play();
							}
						}
						mixer.addEventListener('finished', onFinished);
						return;
					}
				}
				const actionLoop = mixer.clipAction(clipLoop);
				actionLoop.setLoop(THREE.LoopRepeat, Infinity);
				actionLoop.reset().play();
			}

			let isDragging = false;
			let lastX = 0;
			let startX = 0;

			onPointerDown = (e: PointerEvent) => {
				if (e.clientX >= window.innerWidth / 2) return;
				isDragging = true; lastX = e.clientX; startX = e.clientX;
			};
			onPointerMove = (e: PointerEvent) => {
				if (!isDragging) return;
				pivot.rotation.y += (e.clientX - lastX) * 0.008;
				lastX = e.clientX;
			};
			onPointerUp = (e: PointerEvent) => {
				if (isDragging && Math.abs(e.clientX - startX) < 5) {
					animIdx = (animIdx + 1) % ANIMS.length;
					playAnim(ANIMS[animIdx]);
				}
				isDragging = false;
			};

			window.addEventListener('pointerdown', onPointerDown);
			window.addEventListener('pointermove', onPointerMove);
			window.addEventListener('pointerup', onPointerUp);
			window.addEventListener('pointercancel', onPointerUp);

			const clock = new THREE.Clock();

			function tick() {
				raf = requestAnimationFrame(tick);
				const delta = clock.getDelta();
				mixer?.update(delta);
				renderer!.render(scene, camera);
			}

			onResize = () => {
				w = window.innerWidth;
				h = window.innerHeight;
				renderer!.setSize(w, h);
				camera.aspect = w / h;
				camera.updateProjectionMatrix();
			};
			window.addEventListener('resize', onResize);

			const loader = new GLTFLoader();
			loader.load('/models/jhin/scene.gltf', (gltf) => {
				if (destroyed) return;
				const box = new THREE.Box3().setFromObject(gltf.scene);
				const center = box.getCenter(new THREE.Vector3());
				const size = box.getSize(new THREE.Vector3());
				const maxDim = Math.max(size.x, size.y, size.z);

				gltf.scene.position.sub(center);
				pivot.add(gltf.scene);
				pivot.scale.setScalar(2 / maxDim);

				animations = gltf.animations;
				mixer = new THREE.AnimationMixer(gltf.scene);
				loadedScene = gltf.scene;
				playAnim(ANIMS[0]);

				// Force all GLSL shader programs to compile now, while canvas is invisible
				renderer!.compile(scene, camera);

				// Warm up: render several frames invisibly so shader compilation
				// and GPU buffer uploads are fully done before the fade-in starts
				let warmup = 8;
				function warmupTick() {
					if (destroyed) return;
					const delta = clock.getDelta();
					mixer?.update(delta);
					renderer!.render(scene, camera);
					if (--warmup > 0) {
						requestAnimationFrame(warmupTick);
					} else {
						tick(); // hand off to the real loop
						requestAnimationFrame(() => { if (!destroyed) opacity = 0.7; });
					}
				}
				requestAnimationFrame(warmupTick);
			});
		}

		return () => {
			destroyed = true;
			clearTimeout(cancelDelay);
			cancelAnimationFrame(raf);
			if (onResize) window.removeEventListener('resize', onResize);
			if (onPointerDown) window.removeEventListener('pointerdown', onPointerDown);
			if (onPointerMove) window.removeEventListener('pointermove', onPointerMove);
			if (onPointerUp) {
				window.removeEventListener('pointerup', onPointerUp);
				window.removeEventListener('pointercancel', onPointerUp);
			}
			mixer?.stopAllAction();
			if (loadedScene) disposeGltfScene(loadedScene);
			renderer?.dispose();
		};
	});
</script>

<canvas bind:this={canvas} style:opacity={opacity} class="fixed inset-0 z-[1] w-full h-full block pointer-events-none transition-opacity duration-1000 ease-in-out [will-change:opacity]"></canvas>
