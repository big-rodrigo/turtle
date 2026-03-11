<script lang="ts">
	import { tick } from 'svelte';
	import { api, type BookingResponse } from '$lib/api';
	import { statusBadgeClass, statusLabel } from '$lib/status';
	import JhinBackground from '$lib/components/JhinBackground.svelte';
	import YasuoBackground from '$lib/components/YasuoBackground.svelte';
	import { animateStaggerIn, killTweens } from '$lib/gsap';

	const LS_KEY = 'turtle_3d_enabled';

	let modelsEnabled = $state(
		typeof localStorage !== 'undefined' ? localStorage.getItem(LS_KEY) !== 'false' : true
	);

	function toggleModels() {
		modelsEnabled = !modelsEnabled;
		localStorage.setItem(LS_KEY, String(modelsEnabled));
	}

	let bookings = $state<BookingResponse[]>([]);
	let loading = $state(true);
	let error = $state('');
	let listEl = $state<HTMLElement>();
	let staggerTween: gsap.core.Tween | null = null;

	$effect(() => {
		api.getBookings()
			.then((data) => (bookings = data))
			.catch(() => (error = 'Failed to load bookings'))
			.finally(() => (loading = false));
	});

	$effect(() => {
		if (!loading && bookings.length > 0 && listEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > li', listEl);
			});
		}
	});

	function fmt(dt: string) {
		return new Date(dt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
	}
</script>

{#if modelsEnabled}
	<JhinBackground delay={350} />
	<YasuoBackground delay={700} />
{/if}

<div class="page-wrapper select-none">
	<div class="flex items-start justify-between mb-6">
		<div>
			<p class="section-label">SESSION LOG // BOOKINGS</p>
			<h1 class="page-title">My bookings</h1>
		</div>
		<button
			onclick={toggleModels}
			class="btn btn-secondary btn-sm mono mt-1 text-[0.7rem] tracking-widest"
			title={modelsEnabled ? 'Disable 3D models' : 'Enable 3D models'}
		>
			{modelsEnabled ? '[ 3D // ON ]' : '[ 3D // OFF ]'}
		</button>
	</div>

	{#if loading}
		<p class="loading-text">Loading…</p>
	{:else if error}
		<p class="error-box">{error}</p>
	{:else if bookings.length === 0}
		<p class="empty-text">
			No bookings yet. <a href="/coaches" class="text-primary font-semibold">Browse coaches</a>.
		</p>
	{:else}
		<ul class="flex flex-col gap-2 list-none p-0 m-0" bind:this={listEl}>
			{#each bookings as b}
				<li>
					<a href="/bookings/{b.id}" class="panel list-row">
						<div class="flex flex-col gap-[3px]">
							<p class="font-semibold text-text-primary">{b.coachName}</p>
							<p class="text-sm text-text-secondary">{fmt(b.startsAt)}</p>
							<p class="mono text-[0.7rem] text-text-muted">Client: {b.clientName}</p>
						</div>
						<span class="status-badge {statusBadgeClass[b.status]}">{statusLabel[b.status] ?? b.status}</span>
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</div>
