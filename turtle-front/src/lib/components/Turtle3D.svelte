<script lang="ts">
	import * as THREE from 'three';

	let canvasEl: HTMLCanvasElement;

	$effect(() => {
		const scene = new THREE.Scene();
		const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
		camera.position.set(0, 1.5, 5.5);
		camera.lookAt(0, 0, 0);

		const renderer = new THREE.WebGLRenderer({ canvas: canvasEl, alpha: true, antialias: true });
		renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
		renderer.setSize(280, 280);
		renderer.setClearColor(0x000000, 0);

		// Lighting
		scene.add(new THREE.AmbientLight(0x1a3ecf, 0.4));
		const keyLight = new THREE.PointLight(0x4466ff, 2.0, 20);
		keyLight.position.set(3, 4, 3);
		scene.add(keyLight);
		const rimLight = new THREE.PointLight(0x0d1a5c, 0.8, 15);
		rimLight.position.set(-2, -2, -3);
		scene.add(rimLight);

		// Materials
		const shellBodyMat = new THREE.MeshStandardMaterial({
			color: 0x111318,
			metalness: 0.6,
			roughness: 0.4,
			emissive: 0x040812
		});
		const shellWireMat = new THREE.MeshBasicMaterial({
			color: 0x1a3ecf,
			wireframe: true,
			transparent: true,
			opacity: 0.35
		});
		const skinMat = new THREE.MeshStandardMaterial({
			color: 0x191c23,
			metalness: 0.2,
			roughness: 0.7
		});
		const eyeMat = new THREE.MeshBasicMaterial({ color: 0x1a3ecf });

		// Turtle group
		const turtleGroup = new THREE.Group();

		// Shell — dome (top hemisphere, flattened)
		const shellGeo = new THREE.SphereGeometry(1.0, 10, 7, 0, Math.PI * 2, 0, Math.PI * 0.6);
		const shell = new THREE.Mesh(shellGeo, shellBodyMat);
		shell.scale.set(1.0, 0.55, 0.85);
		shell.position.set(0, 0.1, 0);
		turtleGroup.add(shell);

		const shellWire = new THREE.Mesh(shellGeo, shellWireMat);
		shellWire.scale.copy(shell.scale);
		shellWire.position.copy(shell.position);
		turtleGroup.add(shellWire);

		// Plastron — belly plate (bottom hemisphere)
		const plastronGeo = new THREE.SphereGeometry(
			0.9,
			8,
			5,
			0,
			Math.PI * 2,
			Math.PI * 0.4,
			Math.PI * 0.6
		);
		const plastron = new THREE.Mesh(plastronGeo, shellBodyMat);
		plastron.scale.set(0.88, 0.3, 0.75);
		plastron.position.set(0, -0.06, 0);
		plastron.rotation.x = Math.PI;
		turtleGroup.add(plastron);

		// Neck
		const neckGeo = new THREE.CylinderGeometry(0.12, 0.16, 0.35, 6);
		const neck = new THREE.Mesh(neckGeo, skinMat);
		neck.position.set(0, 0.1, 0.72);
		neck.rotation.x = -Math.PI * 0.25;
		turtleGroup.add(neck);

		// Head
		const headGeo = new THREE.SphereGeometry(0.22, 8, 6);
		const head = new THREE.Mesh(headGeo, skinMat);
		head.scale.set(1.0, 0.9, 1.1);
		head.position.set(0, 0.25, 1.05);
		turtleGroup.add(head);

		// Eyes
		const eyeGeo = new THREE.SphereGeometry(0.05, 5, 4);
		const leftEye = new THREE.Mesh(eyeGeo, eyeMat);
		leftEye.position.set(-0.1, 0.33, 1.2);
		const rightEye = new THREE.Mesh(eyeGeo, eyeMat);
		rightEye.position.set(0.1, 0.33, 1.2);
		turtleGroup.add(leftEye, rightEye);

		// Legs
		const frontLegGeo = new THREE.CapsuleGeometry(0.1, 0.45, 4, 6);
		const rearLegGeo = new THREE.CapsuleGeometry(0.1, 0.38, 4, 6);

		const legDefs = [
			{ geo: frontLegGeo, pos: [-0.8, -0.05, 0.5] as const, rz: Math.PI * 0.35, rx: -Math.PI * 0.15 },
			{ geo: frontLegGeo, pos: [0.8, -0.05, 0.5] as const, rz: -Math.PI * 0.35, rx: -Math.PI * 0.15 },
			{ geo: rearLegGeo, pos: [-0.75, -0.05, -0.48] as const, rz: Math.PI * 0.3, rx: Math.PI * 0.12 },
			{ geo: rearLegGeo, pos: [0.75, -0.05, -0.48] as const, rz: -Math.PI * 0.3, rx: Math.PI * 0.12 }
		];
		for (const d of legDefs) {
			const leg = new THREE.Mesh(d.geo, skinMat);
			leg.position.set(d.pos[0], d.pos[1], d.pos[2]);
			leg.rotation.z = d.rz;
			leg.rotation.x = d.rx;
			turtleGroup.add(leg);
		}

		// Tail
		const tailGeo = new THREE.CylinderGeometry(0.04, 0.09, 0.28, 5);
		const tail = new THREE.Mesh(tailGeo, skinMat);
		tail.position.set(0, 0.0, -0.88);
		tail.rotation.x = Math.PI * 0.25;
		turtleGroup.add(tail);

		scene.add(turtleGroup);

		// Animation loop
		let raf: number;
		function tick() {
			raf = requestAnimationFrame(tick);
			turtleGroup.rotation.y += 0.008;
			renderer.render(scene, camera);
		}
		tick();

		// Cleanup
		return () => {
			cancelAnimationFrame(raf);
			[shellGeo, plastronGeo, neckGeo, headGeo, eyeGeo, frontLegGeo, rearLegGeo, tailGeo].forEach(
				(g) => g.dispose()
			);
			[shellBodyMat, shellWireMat, skinMat, eyeMat].forEach((m) => m.dispose());
			renderer.dispose();
		};
	});
</script>

<canvas bind:this={canvasEl} class="w-[280px] h-[280px] block"></canvas>
