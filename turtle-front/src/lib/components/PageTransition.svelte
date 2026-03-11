<script lang="ts">
	import type { Snippet } from 'svelte';
	import { onNavigate } from '$app/navigation';
	import { animatePageExit, animatePageEnter, getDirection } from '$lib/gsap';

	let { children }: { children: Snippet } = $props();

	let container: HTMLElement;

	onNavigate((navigation) => {
		if (!container) return;

		const fromPath = navigation.from?.url.pathname ?? '/';
		const toPath = navigation.to?.url.pathname ?? '/';

		if (fromPath === toPath) return;

		const direction = getDirection(fromPath, toPath);

		return new Promise<() => void>((resolve) => {
			animatePageExit(container, direction).then(() => {
				resolve(() => {
					animatePageEnter(container, direction);
				});
			});
		});
	});
</script>

<div bind:this={container}>
	{@render children()}
</div>
