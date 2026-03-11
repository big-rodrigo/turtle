<script lang="ts">
	import { tick } from 'svelte';
	import { api, type CoachStatusResponse, type CoachStatus } from '$lib/api';
	import { statusBadgeClass } from '$lib/status';
	import { animateStaggerIn, animateGlowFlash, killTweens } from '$lib/gsap';

	let coaches = $state<CoachStatusResponse[]>([]);
	let filter = $state<CoachStatus | ''>('PENDING');
	let loading = $state(true);
	let error = $state('');
	let listEl = $state<HTMLElement>();
	let staggerTween: gsap.core.Tween | null = null;

	async function load() {
		loading = true;
		error = '';
		try {
			coaches = await api.getAdminCoaches(filter || undefined);
		} catch {
			error = 'Failed to load coaches';
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		load();
	});

	$effect(() => {
		if (!loading && coaches.length > 0 && listEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > li', listEl);
			});
		}
	});

	function flashRow(userId: number, glow: string) {
		tick().then(() => {
			const row = listEl?.querySelector(`[data-user-id="${userId}"]`) as HTMLElement | null;
			if (row) animateGlowFlash(row, glow);
		});
	}

	async function approve(userId: number) {
		try {
			const updated = await api.approveCoach(userId);
			coaches = coaches.map((c) => (c.userId === userId ? updated : c));
			flashRow(userId, '0 0 12px rgba(26,122,60,0.8), 0 0 32px rgba(26,122,60,0.3)');
		} catch (err: unknown) {
			error = (err as { message?: string }).message ?? 'Failed';
		}
	}

	async function reject(userId: number) {
		try {
			const updated = await api.rejectCoach(userId);
			coaches = coaches.map((c) => (c.userId === userId ? updated : c));
			flashRow(userId, '0 0 12px rgba(192,21,44,0.8), 0 0 32px rgba(192,21,44,0.3)');
		} catch (err: unknown) {
			error = (err as { message?: string }).message ?? 'Failed';
		}
	}
</script>

<div class="page-wrapper flex flex-col gap-6">
	<div>
		<p class="section-label">ADMIN // COACH APPLICATIONS</p>
		<h1 class="page-title">Coach applications</h1>
	</div>

	<div class="flex gap-2 flex-wrap">
		{#each (['', 'PENDING', 'APPROVED', 'REJECTED'] as const) as s}
			<button
				onclick={() => { filter = s; }}
				class="filter-btn"
				class:filter-btn-active={filter === s}
				class:filter-btn-inactive={filter !== s}
			>
				{s || 'All'}
			</button>
		{/each}
	</div>

	{#if error}
		<p class="error-box">{error}</p>
	{/if}

	{#if loading}
		<p class="loading-text">Loading…</p>
	{:else if coaches.length === 0}
		<p class="empty-text">No coaches in this filter.</p>
	{:else}
		<ul class="flex flex-col gap-2 list-none p-0 m-0" bind:this={listEl}>
			{#each coaches as coach}
				<li class="panel flex flex-col gap-3" data-user-id={coach.userId}>
					<div class="flex items-start justify-between">
						<div class="flex flex-col gap-[3px]">
							<p class="font-semibold text-text-primary">{coach.name}</p>
							<p class="text-sm text-text-secondary">{coach.email}</p>
							<p class="text-sm text-text-muted">{coach.specialty}</p>
						</div>
						<span class="status-badge {statusBadgeClass[coach.status]}">{coach.status}</span>
					</div>
					{#if coach.status === 'PENDING'}
						<div class="flex gap-[10px]">
							<button onclick={() => approve(coach.userId)} class="btn btn-sm btn-success">Approve</button>
							<button onclick={() => reject(coach.userId)} class="btn btn-sm btn-danger">Reject</button>
						</div>
					{/if}
				</li>
			{/each}
		</ul>
	{/if}
</div>
