<script lang="ts">
	import { tick } from 'svelte';
	import { api, type CoachSummary } from '$lib/api';
	import { animateStaggerIn, killTweens } from '$lib/gsap';

	let coaches = $state<CoachSummary[]>([]);
	let loading = $state(true);
	let error = $state('');
	let listEl = $state<HTMLElement>();
	let staggerTween: gsap.core.Tween | null = null;

	$effect(() => {
		api.getCoaches()
			.then((data) => (coaches = data))
			.catch(() => (error = 'Failed to load coaches'))
			.finally(() => (loading = false));
	});

	$effect(() => {
		if (!loading && coaches.length > 0 && listEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > li', listEl);
			});
		}
	});
</script>

<div class="page-wrapper">
	<div>
		<p class="section-label">DIRECTORY // COACHES</p>
		<h1 class="page-title mb-6">Browse coaches</h1>
	</div>

	{#if loading}
		<p class="loading-text">Loading…</p>
	{:else if error}
		<p class="error-box">{error}</p>
	{:else if coaches.length === 0}
		<p class="empty-text">No coaches available yet.</p>
	{:else}
		<ul class="flex flex-col gap-2 list-none p-0 m-0" bind:this={listEl}>
			{#each coaches as coach}
				<li>
					<a href="/coaches/{coach.id}" class="panel list-row">
						<div>
							<p class="font-semibold text-text-primary mb-0.5">{coach.name}</p>
							<p class="text-sm text-text-secondary">{coach.specialty}</p>
						</div>
						<span class="mono text-[0.7rem] text-primary-glow">View →</span>
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</div>
