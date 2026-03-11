<script lang="ts">
	import { onMount } from 'svelte';
	import { token } from '$lib/auth';
	import { animateScaleIn, animatePulse, killTweens } from '$lib/gsap';

	let panelEl: HTMLElement;
	let hourglassEl: HTMLElement;

	onMount(() => {
		const scaleTween = animateScaleIn(panelEl);
		const pulseTween = animatePulse(hourglassEl);
		return () => killTweens(scaleTween, pulseTween);
	});
</script>

<div class="page-centered">
	<div class="panel panel-accent max-w-[440px] w-full" bind:this={panelEl}>
		<p class="section-label mb-4 text-text-secondary">STATUS // PENDING REVIEW</p>
		<div class="text-[2.5rem] mb-4" bind:this={hourglassEl}>⏳</div>
		<h1 class="page-title-md mb-3">Awaiting approval</h1>
		<p class="empty-text text-[0.9375rem] mb-6">
			Your coach account is under review. An admin will approve or reject your application soon.
			Once approved, sign in again to access your dashboard.
		</p>
		<button onclick={() => token.logout()} class="btn btn-secondary">Sign out</button>
	</div>
</div>
