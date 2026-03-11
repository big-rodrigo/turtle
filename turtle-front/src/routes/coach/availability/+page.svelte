<script lang="ts">
	import { tick } from 'svelte';
	import { api, type TimeWindowResponse, type AvailabilitySlot, type CoachingServiceResponse } from '$lib/api';
	import { userId } from '$lib/auth';
	import { DateInput } from '$lib';
	import { animateStaggerIn, killTweens } from '$lib/gsap';

	// ===================== SERVICES =====================
	let services = $state<CoachingServiceResponse[]>([]);
	let servicesLoading = $state(true);
	let servicesError = $state<string | null>(null);

	let showCreateService = $state(false);
	let createServiceForm = $state({ name: '', description: '', extraServiceIds: [] as number[] });
	let creatingService = $state(false);
	let createServiceError = $state<string | null>(null);

	let editingServiceId = $state<number | null>(null);
	let editServiceForm = $state({ name: '', description: '', extraServiceIds: [] as number[] });
	let savingService = $state(false);
	let editServiceError = $state<string | null>(null);

	let confirmDeleteServiceId = $state<number | null>(null);
	let deleteServiceError = $state<string | null>(null);

	let editExtrasOptions = $derived(
		editingServiceId !== null ? services.filter((s) => s.id !== editingServiceId) : services
	);

	// Animation
	let wrapperEl: HTMLElement;
	let staggerTween: gsap.core.Tween | null = null;

	$effect(() => {
		if (!servicesLoading && !windowsLoading && wrapperEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > *', wrapperEl);
			});
		}
		return () => killTweens(staggerTween);
	});

	// ===================== TIME WINDOWS =====================
	let windows = $state<TimeWindowResponse[]>([]);
	let windowsLoading = $state(true);
	let windowsError = $state<string | null>(null);

	let windowServiceId = $state<number | null>(null);
	let form = $state({
		startDate: '',
		endDate: '',
		dailyStartTime: '',
		dailyEndTime: '',
		unitOfWorkMinutes: 30,
		pricePerUnit: null as number | null
	});
	let creating = $state(false);
	let createError = $state<string | null>(null);

	let confirmDeleteId = $state<number | null>(null);
	let deleteError = $state<string | null>(null);

	let dragSrcIndex = $state<number | null>(null);
	let dragOverIndex = $state<number | null>(null);
	let reorderError = $state<string | null>(null);

	// ===================== SLOT INSPECTOR =====================
	let inspectDate = $state('');
	let inspectedSlots = $state<AvailabilitySlot[]>([]);
	let inspectLoading = $state(false);
	let inspectError = $state<string | null>(null);

	// ===================== EFFECTS =====================
	$effect(() => {
		if ($userId === null) return;
		loadServices();
		loadWindows();
	});

	$effect(() => {
		if (!inspectDate) {
			inspectedSlots = [];
			return;
		}
		if ($userId === null) return;
		inspectLoading = true;
		inspectError = null;
		api
			.getSlotsByDate($userId, inspectDate)
			.then((d) => (inspectedSlots = d))
			.catch((e: { message?: string }) => (inspectError = e.message ?? 'Failed to load slots'))
			.finally(() => (inspectLoading = false));
	});

	// ===================== SERVICE METHODS =====================
	async function loadServices() {
		servicesLoading = true;
		servicesError = null;
		try {
			services = await api.getCoachingServices($userId!);
		} catch (e: unknown) {
			servicesError = (e as { message?: string }).message ?? 'Failed to load services';
		} finally {
			servicesLoading = false;
		}
	}

	async function createService(e: Event) {
		e.preventDefault();
		creatingService = true;
		createServiceError = null;
		try {
			const svc = await api.createCoachingService($userId!, {
				name: createServiceForm.name,
				description: createServiceForm.description || undefined,
				extraServiceIds:
					createServiceForm.extraServiceIds.length > 0
						? createServiceForm.extraServiceIds
						: undefined
			});
			services = [...services, svc];
			showCreateService = false;
			createServiceForm = { name: '', description: '', extraServiceIds: [] };
		} catch (e: unknown) {
			createServiceError = (e as { message?: string }).message ?? 'Failed to create service';
		} finally {
			creatingService = false;
		}
	}

	function startEdit(svc: CoachingServiceResponse) {
		editingServiceId = svc.id;
		editServiceForm = {
			name: svc.name,
			description: svc.description ?? '',
			extraServiceIds: svc.extras.map((ex) => ex.id)
		};
		editServiceError = null;
		showCreateService = false;
	}

	async function saveService(e: Event) {
		e.preventDefault();
		if (!editingServiceId) return;
		savingService = true;
		editServiceError = null;
		try {
			const svc = await api.updateCoachingService(editingServiceId, {
				name: editServiceForm.name,
				description: editServiceForm.description || undefined,
				extraServiceIds:
					editServiceForm.extraServiceIds.length > 0 ? editServiceForm.extraServiceIds : undefined
			});
			services = services.map((s) => (s.id === svc.id ? svc : s));
			editingServiceId = null;
		} catch (e: unknown) {
			editServiceError = (e as { message?: string }).message ?? 'Failed to save service';
		} finally {
			savingService = false;
		}
	}

	async function deleteService(id: number) {
		deleteServiceError = null;
		try {
			await api.deleteCoachingService(id);
			services = services.filter((s) => s.id !== id);
			confirmDeleteServiceId = null;
			await loadWindows();
		} catch (e: unknown) {
			deleteServiceError = (e as { message?: string }).message ?? 'Failed to delete service';
			confirmDeleteServiceId = null;
		}
	}

	function toggleExtra(extraId: number, which: 'create' | 'edit') {
		if (which === 'create') {
			const ids = createServiceForm.extraServiceIds;
			createServiceForm.extraServiceIds = ids.includes(extraId)
				? ids.filter((id) => id !== extraId)
				: [...ids, extraId];
		} else {
			const ids = editServiceForm.extraServiceIds;
			editServiceForm.extraServiceIds = ids.includes(extraId)
				? ids.filter((id) => id !== extraId)
				: [...ids, extraId];
		}
	}

	// ===================== WINDOW METHODS =====================
	async function loadWindows() {
		windowsLoading = true;
		windowsError = null;
		try {
			windows = await api.getTimeWindows($userId!);
		} catch (e: unknown) {
			windowsError = (e as { message?: string }).message ?? 'Failed to load time windows';
		} finally {
			windowsLoading = false;
		}
	}

	async function createWindow(e: Event) {
		e.preventDefault();
		if (!windowServiceId) return;
		creating = true;
		createError = null;
		try {
			const w = await api.createTimeWindow($userId!, {
				...form,
				serviceId: windowServiceId,
				unitOfWorkMinutes: Number(form.unitOfWorkMinutes),
				pricePerUnit: form.pricePerUnit ?? undefined,
				priority: 0
			});
			windows = [...windows, w];
			await reorderWindows();
			form = { startDate: '', endDate: '', dailyStartTime: '', dailyEndTime: '', unitOfWorkMinutes: 30, pricePerUnit: null };
		} catch (e: unknown) {
			createError = (e as { message?: string }).message ?? 'Failed to create time window';
		} finally {
			creating = false;
		}
	}

	async function deleteWindow(id: number) {
		deleteError = null;
		try {
			await api.deleteTimeWindow(id);
			windows = windows.filter((w) => w.id !== id);
			confirmDeleteId = null;
			if (inspectDate) {
				inspectedSlots = await api.getSlotsByDate($userId!, inspectDate);
			}
		} catch (e: unknown) {
			deleteError = (e as { message?: string }).message ?? 'Failed to delete time window';
			confirmDeleteId = null;
		}
	}

	async function reorderWindows() {
		reorderError = null;
		const updates = windows.map((w, i) => ({ id: w.id, priority: windows.length - i }));
		try {
			await api.reorderTimeWindows($userId!, updates);
			windows = windows.map((w, i) => ({ ...w, priority: windows.length - i }));
		} catch (e: unknown) {
			reorderError = (e as { message?: string }).message ?? 'Failed to save priority order';
		}
	}

	function onDragStart(index: number) {
		dragSrcIndex = index;
	}
	function onDragOver(e: DragEvent, index: number) {
		e.preventDefault();
		dragOverIndex = index;
	}
	function onDragLeave() {
		dragOverIndex = null;
	}
	async function onDrop(targetIndex: number) {
		if (dragSrcIndex === null || dragSrcIndex === targetIndex) {
			dragSrcIndex = null;
			dragOverIndex = null;
			return;
		}
		const updated = [...windows];
		const [moved] = updated.splice(dragSrcIndex, 1);
		updated.splice(targetIndex, 0, moved);
		windows = updated;
		dragSrcIndex = null;
		dragOverIndex = null;
		await reorderWindows();
	}
</script>

<div class="page-wrapper-lg flex flex-col gap-10" bind:this={wrapperEl}>
	<div>
		<p class="section-label">COACH // AVAILABILITY MANAGER</p>
		<h1 class="page-title">Availability</h1>
	</div>

	<!-- ========== SECTION 1: COACHING SERVICES ========== -->
	<section class="flex flex-col gap-4">
		<div class="flex items-center justify-between">
			<h2 class="section-heading">Coaching Services</h2>
			<button
				onclick={() => {
					showCreateService = !showCreateService;
					editingServiceId = null;
				}}
				class="btn btn-secondary btn-sm"
			>
				{showCreateService ? 'Cancel' : '+ Add Service'}
			</button>
		</div>

		{#if deleteServiceError}
			<p class="error-box">{deleteServiceError}</p>
		{/if}

		{#if servicesLoading}
			<p class="loading-text">Loading…</p>
		{:else if servicesError}
			<p class="error-box">{servicesError}</p>
		{:else if services.length === 0 && !showCreateService}
			<div class="panel">
				<p class="empty-text">No services defined. Add one to start building your schedule.</p>
			</div>
		{:else if services.length > 0}
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
							<tr class:row-editing={editingServiceId === svc.id}>
								<td class="font-semibold text-text-primary">{svc.name}</td>
								<td class="text-sm text-text-secondary max-w-xs truncate">{svc.description ?? '—'}</td>
								<td>
									{#if svc.extras.length > 0}
										<div class="flex flex-wrap gap-1">
											{#each svc.extras as extra}
												<span class="slot-status slot-available">{extra.name}</span>
											{/each}
										</div>
									{:else}
										<span class="text-text-muted text-sm">—</span>
									{/if}
								</td>
								<td class="text-right whitespace-nowrap">
									{#if confirmDeleteServiceId === svc.id}
										<span class="text-[0.75rem] text-text-muted mr-2">Delete?</span>
										<button
											onclick={() => deleteService(svc.id)}
											class="btn btn-sm btn-danger mr-1">Confirm</button
										>
										<button
											onclick={() => (confirmDeleteServiceId = null)}
											class="btn btn-sm btn-secondary">Cancel</button
										>
									{:else}
										<button
											onclick={() => startEdit(svc)}
											class="btn btn-sm btn-secondary mr-1">Edit</button
										>
										<button
											onclick={() => {
												confirmDeleteServiceId = svc.id;
												editingServiceId = null;
											}}
											class="btn btn-sm btn-ghost-danger">Delete</button
										>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/if}

		<!-- Create Service Form -->
		{#if showCreateService}
			<div class="panel panel-accent flex flex-col gap-4">
				<p class="section-label">NEW SERVICE</p>
				{#if createServiceError}
					<p class="error-box">{createServiceError}</p>
				{/if}
				<form onsubmit={createService} class="flex flex-col gap-4">
					<div class="grid grid-cols-2 gap-4">
						<div class="col-span-2">
							<label class="form-label" for="svc-name">Name *</label>
							<input
								id="svc-name"
								type="text"
								bind:value={createServiceForm.name}
								required
								maxlength="200"
								placeholder="e.g. Career Coaching Session"
								class="h-10 w-full px-3 text-sm"
							/>
						</div>
						<div class="col-span-2">
							<label class="form-label" for="svc-desc">Description</label>
							<textarea
								id="svc-desc"
								bind:value={createServiceForm.description}
								rows="2"
								placeholder="What's included in this service?"
								class="w-full px-3 py-2 text-sm"
							></textarea>
						</div>
					</div>
					{#if services.length > 0}
						<div>
							<p class="form-label">Add-ons (optional extras from your other services)</p>
							<div class="flex flex-wrap gap-4 mt-2">
								{#each services as svc}
									<label class="flex items-center gap-2 cursor-pointer text-sm text-text-secondary">
										<input
											type="checkbox"
											checked={createServiceForm.extraServiceIds.includes(svc.id)}
											onchange={() => toggleExtra(svc.id, 'create')}
										/>
										{svc.name}
									</label>
								{/each}
							</div>
						</div>
					{/if}
					<div>
						<button
							type="submit"
							disabled={creatingService}
							class="btn btn-primary"
							style:opacity={creatingService ? 0.5 : 1}
						>
							{creatingService ? 'Creating…' : 'Create Service'}
						</button>
					</div>
				</form>
			</div>
		{/if}

		<!-- Edit Service Form -->
		{#if editingServiceId !== null}
			<div class="panel panel-accent flex flex-col gap-4">
				<div class="flex items-center justify-between">
					<p class="section-label">EDIT SERVICE</p>
					<button onclick={() => (editingServiceId = null)} class="btn btn-sm btn-secondary"
						>Cancel</button
					>
				</div>
				{#if editServiceError}
					<p class="error-box">{editServiceError}</p>
				{/if}
				<form onsubmit={saveService} class="flex flex-col gap-4">
					<div class="grid grid-cols-2 gap-4">
						<div class="col-span-2">
							<label class="form-label" for="edit-svc-name">Name *</label>
							<input
								id="edit-svc-name"
								type="text"
								bind:value={editServiceForm.name}
								required
								maxlength="200"
								class="h-10 w-full px-3 text-sm"
							/>
						</div>
						<div class="col-span-2">
							<label class="form-label" for="edit-svc-desc">Description</label>
							<textarea
								id="edit-svc-desc"
								bind:value={editServiceForm.description}
								rows="2"
								class="w-full px-3 py-2 text-sm"
							></textarea>
						</div>
					</div>
					{#if editExtrasOptions.length > 0}
						<div>
							<p class="form-label">Add-ons (optional extras from your other services)</p>
							<div class="flex flex-wrap gap-4 mt-2">
								{#each editExtrasOptions as svc}
									<label class="flex items-center gap-2 cursor-pointer text-sm text-text-secondary">
										<input
											type="checkbox"
											checked={editServiceForm.extraServiceIds.includes(svc.id)}
											onchange={() => toggleExtra(svc.id, 'edit')}
										/>
										{svc.name}
									</label>
								{/each}
							</div>
						</div>
					{/if}
					<div>
						<button
							type="submit"
							disabled={savingService}
							class="btn btn-primary"
							style:opacity={savingService ? 0.5 : 1}
						>
							{savingService ? 'Saving…' : 'Save Changes'}
						</button>
					</div>
				</form>
			</div>
		{/if}
	</section>

	<hr />

	<!-- ========== SECTION 2: TIME WINDOWS ========== -->
	<section class="flex flex-col gap-4">
		<h2 class="section-heading">Time Windows</h2>

		{#if deleteError}
			<p class="error-box">{deleteError}</p>
		{/if}
		{#if reorderError}
			<p class="error-box">{reorderError}</p>
		{/if}

		{#if windowsLoading}
			<p class="loading-text">Loading…</p>
		{:else if windowsError}
			<p class="error-box">{windowsError}</p>
		{:else if windows.length === 0}
			<p class="empty-text">No time windows defined.</p>
		{:else}
			<div class="panel">
				<table>
					<thead>
						<tr>
							<th class="w-8 p-2"></th>
							<th>Service</th>
							<th>Date Range</th>
							<th>Daily Hours</th>
							<th>Slot</th>
							<th>Price</th>
							<th>Priority</th>
							<th></th>
						</tr>
					</thead>
					<tbody>
						{#each windows as w, i}
							<tr
								style:opacity={dragSrcIndex === i ? 0.4 : 1}
								style:border-top={dragOverIndex === i ? '2px solid var(--color-primary)' : 'none'}
								draggable="true"
								ondragstart={() => onDragStart(i)}
								ondragover={(e) => onDragOver(e, i)}
								ondragleave={onDragLeave}
								ondrop={() => onDrop(i)}
							>
								<td class="p-2 text-center text-text-muted cursor-grab select-none">⠿</td>
								<td class="font-semibold text-sm {w.serviceName ? 'text-text-primary' : 'text-text-muted'}"
									>{w.serviceName ?? '—'}</td
								>
								<td class="mono text-[0.8rem]">{w.startDate} – {w.endDate}</td>
								<td class="text-sm text-text-secondary">{w.dailyStartTime} – {w.dailyEndTime}</td>
								<td class="mono text-[0.8rem]">{w.unitOfWorkMinutes} min</td>
								<td class="mono text-[0.8rem]">{w.pricePerUnit != null ? `$${Number(w.pricePerUnit).toFixed(2)}` : '—'}</td>
								<td class="mono text-[0.8rem] text-primary">{w.priority}</td>
								<td class="text-right">
									{#if confirmDeleteId === w.id}
										<span class="text-[0.75rem] text-text-muted mr-2">Delete?</span>
										<button
											onclick={() => deleteWindow(w.id)}
											class="btn btn-sm btn-danger mr-1">Confirm</button
										>
										<button
											onclick={() => (confirmDeleteId = null)}
											class="btn btn-sm btn-secondary">Cancel</button
										>
									{:else}
										<button
											onclick={() => (confirmDeleteId = w.id)}
											class="btn btn-sm btn-ghost-danger">Delete</button
										>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/if}

		<!-- Create Time Window Form -->
		<div class="flex flex-col gap-2">
			<h3 class="section-heading text-sm">Create Time Window</h3>
			{#if createError}
				<p class="error-box">{createError}</p>
			{/if}
		</div>

		{#if services.length === 0 && !servicesLoading}
			<div class="panel">
				<p class="empty-text">
					Create at least one service above before adding time windows.
				</p>
			</div>
		{:else}
			<form onsubmit={createWindow} class="panel flex flex-col gap-4">
				<div class="grid grid-cols-2 gap-4">
					<div class="col-span-2">
						<label class="form-label" for="window-service">Service *</label>
						<select
							id="window-service"
							bind:value={windowServiceId}
							required
							class="h-10 w-full px-3 text-sm"
						>
							<option value={null} disabled selected>Select a service…</option>
							{#each services as svc}
								<option value={svc.id}>{svc.name}</option>
							{/each}
						</select>
					</div>
					<div>
						<label class="form-label" for="startDate">Start date</label>
						<DateInput id="startDate" bind:value={form.startDate} required class="w-full" />
					</div>
					<div>
						<label class="form-label" for="endDate">End date</label>
						<DateInput id="endDate" bind:value={form.endDate} required class="w-full" />
					</div>
					<div>
						<label class="form-label" for="dailyStartTime">Daily start time</label>
						<input
							id="dailyStartTime"
							type="time"
							bind:value={form.dailyStartTime}
							required
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
					<div>
						<label class="form-label" for="dailyEndTime">Daily end time</label>
						<input
							id="dailyEndTime"
							type="time"
							bind:value={form.dailyEndTime}
							required
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
					<div>
						<label class="form-label" for="unitOfWorkMinutes">Slot duration (min)</label>
						<input
							id="unitOfWorkMinutes"
							type="number"
							bind:value={form.unitOfWorkMinutes}
							min="5"
							required
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
					<div>
						<label class="form-label" for="pricePerUnit">Price per slot (USD, optional)</label>
						<input
							id="pricePerUnit"
							type="number"
							bind:value={form.pricePerUnit}
							min="0"
							step="0.01"
							placeholder="e.g. 150.00"
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
				</div>
				<div>
					<button
						type="submit"
						disabled={creating || !windowServiceId}
						class="btn btn-primary"
						style:opacity={creating || !windowServiceId ? 0.5 : 1}
					>
						{creating ? 'Creating…' : 'Create'}
					</button>
				</div>
			</form>
		{/if}
	</section>

	<hr />

	<!-- ========== SECTION 3: INSPECT SLOTS ========== -->
	<section class="flex flex-col gap-4">
		<h2 class="section-heading">Inspect Slots</h2>

		<div class="panel flex flex-col gap-2">
			<label class="form-label" for="inspect-date">Select a date</label>
			<DateInput id="inspect-date" bind:value={inspectDate} />
		</div>

		{#if inspectDate}
			{#if inspectLoading}
				<p class="loading-text">Loading slots…</p>
			{:else if inspectError}
				<p class="error-box">{inspectError}</p>
			{:else if inspectedSlots.length === 0}
				<p class="empty-text">No slots for this date.</p>
			{:else}
				<div class="panel">
					<table>
						<thead>
							<tr>
								<th>Starts</th>
								<th>Ends</th>
								<th>Service</th>
								<th>Status</th>
							</tr>
						</thead>
						<tbody>
							{#each inspectedSlots as slot}
								<tr>
									<td class="mono text-[0.8rem]">
										{new Date(slot.startsAt).toLocaleTimeString(undefined, { timeStyle: 'short' })}
									</td>
									<td class="mono text-[0.8rem]">
										{new Date(slot.endsAt).toLocaleTimeString(undefined, { timeStyle: 'short' })}
									</td>
									<td
										class="text-sm {slot.serviceName
											? 'text-text-primary'
											: 'text-text-muted'}">{slot.serviceName ?? '—'}</td
									>
									<td>
										<span
											class="slot-status"
											class:slot-available={slot.status === 'AVAILABLE'}
											class:slot-booked={slot.status !== 'AVAILABLE'}>{slot.status}</span
										>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
			{/if}
		{/if}
	</section>
</div>

<style>
	input[type='checkbox'] {
		accent-color: var(--color-primary);
		width: 14px;
		height: 14px;
		cursor: pointer;
	}

	tr.row-editing {
		background: rgba(26, 62, 207, 0.07);
	}
</style>
