<script lang="ts">
	import { tick } from 'svelte';
	import { api, type CoachingServiceResponse } from '$lib/api';
	import { userId } from '$lib/auth';
	import { animateStaggerIn, animateSlideUp, killTweens } from '$lib/gsap';

	let services = $state<CoachingServiceResponse[]>([]);
	let loading = $state(true);
	let loadError = $state<string | null>(null);

	// Create form
	let createForm = $state({ name: '', description: '' });
	let creating = $state(false);
	let createError = $state<string | null>(null);

	// Edit
	let editingId = $state<number | null>(null);
	let editForm = $state({ name: '', description: '', extraServiceIds: new Set<number>() });
	let saving = $state(false);
	let editError = $state<string | null>(null);

	// Delete confirm
	let confirmDeleteId = $state<number | null>(null);
	let deleteError = $state<string | null>(null);

	// Animation
	let wrapperEl: HTMLElement;
	let staggerTween: gsap.core.Tween | null = null;

	$effect(() => {
		if (!loading && services.length > 0 && wrapperEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > *', wrapperEl);
			});
		}
		return () => killTweens(staggerTween);
	});

	// Services eligible as extras: other services that have no extras of their own
	let eligibleExtras = $derived(
		services.filter((s) => s.id !== editingId && s.extras.length === 0)
	);

	$effect(() => {
		if ($userId === null) return;
		api
			.getCoachingServices($userId)
			.then((data) => (services = data))
			.catch((e: { message?: string }) => (loadError = e.message ?? 'Failed to load services'))
			.finally(() => (loading = false));
	});

	function startEdit(svc: CoachingServiceResponse) {
		editingId = svc.id;
		editForm = {
			name: svc.name,
			description: svc.description ?? '',
			extraServiceIds: new Set(svc.extras.map((e) => e.id))
		};
		editError = null;
	}

	function toggleExtra(id: number) {
		const next = new Set(editForm.extraServiceIds);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		editForm = { ...editForm, extraServiceIds: next };
	}

	async function saveEdit() {
		if (editingId === null || $userId === null) return;
		saving = true;
		editError = null;
		try {
			const updated = await api.updateCoachingService(editingId, {
				name: editForm.name,
				description: editForm.description || undefined,
				extraServiceIds: [...editForm.extraServiceIds]
			});
			services = services.map((s) => (s.id === editingId ? updated : s));
			editingId = null;
		} catch (e: unknown) {
			editError = (e as { message?: string }).message ?? 'Failed to save';
		} finally {
			saving = false;
		}
	}

	async function deleteService(id: number) {
		if ($userId === null) return;
		deleteError = null;
		try {
			await api.deleteCoachingService(id);
			services = services.filter((s) => s.id !== id);
			confirmDeleteId = null;
			if (editingId === id) editingId = null;
		} catch (e: unknown) {
			deleteError = (e as { message?: string }).message ?? 'Failed to delete';
			confirmDeleteId = null;
		}
	}

	async function createService(e: Event) {
		e.preventDefault();
		if ($userId === null) return;
		creating = true;
		createError = null;
		try {
			const svc = await api.createCoachingService($userId, {
				name: createForm.name,
				description: createForm.description || undefined
			});
			services = [...services, svc];
			createForm = { name: '', description: '' };
		} catch (e: unknown) {
			createError = (e as { message?: string }).message ?? 'Failed to create';
		} finally {
			creating = false;
		}
	}
</script>

<div class="page-wrapper-lg flex flex-col gap-8" bind:this={wrapperEl}>
	<div>
		<p class="section-label">COACH // SERVICES MANAGER</p>
		<h1 class="page-title">Services</h1>
	</div>

	<!-- Section 1: Services List -->
	<section class="flex flex-col gap-4">
		<h2 class="section-heading">Your Services</h2>

		{#if deleteError}
			<p class="error-box">{deleteError}</p>
		{/if}

		{#if loading}
			<p class="loading-text">Loading…</p>
		{:else if loadError}
			<p class="error-box">{loadError}</p>
		{:else if services.length === 0}
			<p class="empty-text">No services defined yet.</p>
		{:else}
			<div class="panel">
				<table>
					<thead>
						<tr>
							<th>Name</th>
							<th>Description</th>
							<th>Add-ons</th>
							<th></th>
						</tr>
					</thead>
					<tbody>
						{#each services as svc}
							<tr>
								<td class="font-semibold text-text-primary">{svc.name}</td>
								<td class="text-sm text-text-secondary">{svc.description ?? '—'}</td>
								<td>
									{#if svc.extras.length > 0}
										<div class="flex flex-wrap gap-1">
											{#each svc.extras as extra}
												<span class="slot-status slot-available">{extra.name}</span>
											{/each}
										</div>
									{:else}
										<span class="text-text-muted">—</span>
									{/if}
								</td>
								<td class="text-right">
									{#if confirmDeleteId === svc.id}
										<span class="text-[0.75rem] text-text-muted mr-2">Delete this service?</span>
										<button
											onclick={() => deleteService(svc.id)}
											class="btn btn-sm btn-danger mr-1"
										>Confirm</button>
										<button
											onclick={() => (confirmDeleteId = null)}
											class="btn btn-sm btn-secondary"
										>Cancel</button>
									{:else}
										<button
											onclick={() => startEdit(svc)}
											class="btn btn-sm btn-secondary mr-1"
										>Edit</button>
										<button
											onclick={() => (confirmDeleteId = svc.id)}
											class="btn btn-sm btn-ghost-danger"
										>Delete</button>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/if}
	</section>

	<!-- Edit panel -->
	{#if editingId !== null}
		<section class="flex flex-col gap-4">
			<h2 class="section-heading">Edit Service</h2>

			{#if editError}
				<p class="error-box">{editError}</p>
			{/if}

			<div class="panel panel-accent flex flex-col gap-4">
				<div class="grid grid-cols-2 gap-4">
					<div>
						<label class="form-label" for="edit-name">Name</label>
						<input
							id="edit-name"
							type="text"
							bind:value={editForm.name}
							required
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
					<div>
						<label class="form-label" for="edit-desc">Description</label>
						<input
							id="edit-desc"
							type="text"
							bind:value={editForm.description}
							placeholder="Optional"
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
				</div>

				{#if eligibleExtras.length > 0}
					<div class="flex flex-col gap-2">
						<p class="form-label">Add-ons (optional)</p>
						<p class="text-xs text-text-muted">Only services with no add-ons of their own can be selected.</p>
						<div class="flex flex-col gap-2">
							{#each eligibleExtras as extra}
								<label class="flex items-start gap-3 cursor-pointer">
									<input
										type="checkbox"
										checked={editForm.extraServiceIds.has(extra.id)}
										onchange={() => toggleExtra(extra.id)}
										class="mt-0.5 shrink-0"
									/>
									<div class="flex flex-col gap-0.5">
										<span class="text-sm font-semibold text-text-primary">{extra.name}</span>
										{#if extra.description}
											<span class="text-xs text-text-secondary">{extra.description}</span>
										{/if}
									</div>
								</label>
							{/each}
						</div>
					</div>
				{:else if services.length > 1}
					<p class="text-xs text-text-muted">No services are eligible as add-ons (they must have no add-ons of their own).</p>
				{/if}

				<div class="flex gap-3">
					<button
						onclick={saveEdit}
						disabled={saving}
						class="btn btn-primary"
						style:opacity={saving ? 0.5 : 1}
					>{saving ? 'Saving…' : 'Save'}</button>
					<button
						onclick={() => (editingId = null)}
						class="btn btn-secondary"
					>Cancel</button>
				</div>
			</div>
		</section>
	{/if}

	<hr />

	<!-- Section 2: Create Service -->
	<section class="flex flex-col gap-4">
		<h2 class="section-heading">Create Service</h2>

		{#if createError}
			<p class="error-box">{createError}</p>
		{/if}

		<form onsubmit={createService} class="panel flex flex-col gap-4">
			<div class="grid grid-cols-2 gap-4">
				<div>
					<label class="form-label" for="create-name">Name</label>
					<input
						id="create-name"
						type="text"
						bind:value={createForm.name}
						required
						placeholder="e.g. 1-on-1 Session"
						class="h-10 w-full px-3 text-sm"
					/>
				</div>
				<div>
					<label class="form-label" for="create-desc">Description</label>
					<input
						id="create-desc"
						type="text"
						bind:value={createForm.description}
						placeholder="Optional"
						class="h-10 w-full px-3 text-sm"
					/>
				</div>
			</div>
			<div>
				<button
					type="submit"
					disabled={creating}
					class="btn btn-primary"
					style:opacity={creating ? 0.5 : 1}
				>{creating ? 'Creating…' : 'Create'}</button>
			</div>
		</form>
	</section>
</div>

<style>
	input[type='checkbox'] {
		accent-color: var(--color-primary);
		width: 16px;
		height: 16px;
		cursor: pointer;
	}
</style>
