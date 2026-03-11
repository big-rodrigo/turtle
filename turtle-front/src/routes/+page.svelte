<script lang="ts">
	import { onMount } from 'svelte';
	import { role } from '$lib/auth';
	import { goto } from '$app/navigation';
	import TurtleComparison from '$lib/components/TurtleComparison.svelte';
	import { animateStaggerIn, killTweens } from '$lib/gsap';

	$effect(() => {
		if ($role === 'CLIENT' || $role === 'COACH') goto('/bookings');
		else if ($role === 'COACH_PENDING') goto('/pending-approval');
		else if ($role === 'ADMIN') goto('/admin/coaches');
	});

	let wrapper: HTMLElement;

	onMount(() => {
		const tween = animateStaggerIn(':scope > *', wrapper);
		return () => killTweens(tween);
	});
</script>

<div class="page-centered select-none" bind:this={wrapper}>
	<div>
		<p class="section-label mb-3 text-[0.7rem] tracking-[0.2em] text-shadow-primary-md">
			SYSTEM ONLINE // v1.0.0
		</p>
		<h1 class="page-title-lg">
			Turtle<br /><span class="text-primary text-shadow-primary-lg">Coaching</span>
		</h1>
	</div>

	<TurtleComparison />

	<p class="empty-text max-w-[360px] text-[0.9375rem]">
		Book sessions with experienced coaches and grow together.
	</p>

	<div class="flex gap-3">
		<a href="/coaches" class="btn btn-primary btn-lg">Browse Coaches</a>
		<a href="/login" class="btn btn-secondary btn-lg">Sign in</a>
	</div>
</div>
