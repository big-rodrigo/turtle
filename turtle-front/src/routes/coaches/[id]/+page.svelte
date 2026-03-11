<script lang="ts">
	import { tick } from 'svelte';
	import { page } from '$app/state';
	import { api, type AvailabilitySlot, type CoachingServiceResponse, type TimeWindowResponse, type CoachProfile } from '$lib/api';
	import { role } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { DateInput, BookingCalendar, ProfileCard } from '$lib';
	import { animateWizardStep, animateStaggerIn, killTweens } from '$lib/gsap';

	const coachId = Number(page.params.id);

	// Coach profile
	let coachProfile = $state<CoachProfile | null>(null);

	$effect(() => {
		api.getCoachProfile(coachId)
			.then((p) => (coachProfile = p))
			.catch(() => {});
	});

	// Services
	let services = $state<CoachingServiceResponse[]>([]);
	let servicesLoading = $state(true);
	let servicesError = $state<string | null>(null);

	// Time windows (for pricing)
	let timeWindows = $state<TimeWindowResponse[]>([]);

	// Step
	type Step = 'service' | 'slots' | 'confirm';
	let step = $state<Step>('service');

	// Step 1
	let selectedService = $state<CoachingServiceResponse | null>(null);

	// Step 2
	let selectedDate = $state('');
	let slots = $state<AvailabilitySlot[]>([]);
	let slotsLoading = $state(false);
	let slotsError = $state<string | null>(null);
	let selectedSlotIds = $state<Set<number>>(new Set());

	// Step 3
	let selectedExtraIds = $state<Set<number>>(new Set());
	let notes = $state('');
	let bookingLoading = $state(false);
	let bookingError = $state<string | null>(null);

	// Derived
	let filteredSlots = $derived(
		selectedService === null
			? slots
			: slots.filter((s) => s.serviceId === selectedService!.id)
	);

	let sortedSelectedSlots = $derived(
		slots
			.filter((s) => selectedSlotIds.has(s.id))
			.sort((a, b) => a.startsAt.localeCompare(b.startsAt))
	);

	let canProceed = $derived(selectedSlotIds.size > 0);

	function priceLabel(windows: TimeWindowResponse[], serviceId: number | null): string | null {
		const prices = windows
			.filter((w) => w.serviceId === serviceId && w.pricePerUnit != null)
			.map((w) => Number(w.pricePerUnit));
		if (prices.length === 0) return null;
		const min = Math.min(...prices);
		const max = Math.max(...prices);
		return min === max
			? `$${min.toFixed(2)} / session`
			: `$${min.toFixed(2)} – $${max.toFixed(2)} / session`;
	}

	let servicePriceLabels = $derived(
		new Map(services.map((svc) => [svc.id, priceLabel(timeWindows, svc.id)]))
	);

	let sessionPriceLabel = $derived(
		selectedService !== null ? priceLabel(timeWindows, selectedService.id) : null
	);

	// Load services on mount
	$effect(() => {
		api
			.getCoachingServices(coachId)
			.then((data) => {
				services = data;
				if (data.length === 0) step = 'slots';
			})
			.catch((e: { message?: string }) => (servicesError = e.message ?? 'Failed to load services'))
			.finally(() => (servicesLoading = false));
	});

	// Load time windows for pricing context
	$effect(() => {
		api.getTimeWindows(coachId).then((data) => (timeWindows = data)).catch(() => {});
	});

	// Load slots when date or service changes
	$effect(() => {
		if (!selectedDate) {
			slots = [];
			selectedSlotIds = new Set();
			return;
		}
		slotsLoading = true;
		slotsError = null;
		const fetch =
			selectedService !== null
				? api.getSlotsByService(selectedService.id, selectedDate)
				: api.getSlotsByDate(coachId, selectedDate);
		fetch
			.then((data) => {
				slots = data;
				selectedSlotIds = new Set();
			})
			.catch((e: { message?: string }) => (slotsError = e.message ?? 'Failed to load slots'))
			.finally(() => (slotsLoading = false));
	});

	function selectService(svc: CoachingServiceResponse | null) {
		selectedService = svc;
		step = 'slots';
	}

	function toggleSlot(slotId: number) {
		const next = new Set(selectedSlotIds);
		if (next.has(slotId)) next.delete(slotId);
		else next.add(slotId);
		selectedSlotIds = next;
	}

	function toggleExtra(extraId: number) {
		const next = new Set(selectedExtraIds);
		if (next.has(extraId)) next.delete(extraId);
		else next.add(extraId);
		selectedExtraIds = next;
	}

	async function confirmBooking() {
		bookingError = null;
		bookingLoading = true;
		try {
			const serviceId =
				selectedService?.id ??
				slots.find((s) => selectedSlotIds.has(s.id))?.serviceId ??
				0;
			const booking = await api.createBooking({
				availabilityIds: [...selectedSlotIds],
				serviceId,
				notes: notes.trim() || undefined,
				extraServiceIds: selectedExtraIds.size > 0 ? [...selectedExtraIds] : undefined
			});
			goto(`/bookings/${booking.id}?action=pay`);
		} catch (err: unknown) {
			bookingError = (err as { message?: string }).message ?? 'Booking failed';
		} finally {
			bookingLoading = false;
		}
	}

	function formatTime(dt: string) {
		return new Date(dt).toLocaleTimeString(undefined, { timeStyle: 'short' });
	}

	function formatDateTime(dt: string) {
		return new Date(dt).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
	}

	let stepIndex = $derived(step === 'service' ? 0 : step === 'slots' ? 1 : 2);

	// Wizard step transition animations
	let prevStep: Step | null = null;
	let stepEl = $state<HTMLElement>();
	let stepTween: gsap.core.Tween | null = null;
	const STEP_ORDER: Record<Step, number> = { service: 0, slots: 1, confirm: 2 };

	$effect(() => {
		const current = step;
		const el = stepEl;
		if (prevStep !== null && prevStep !== current && el) {
			const forward = STEP_ORDER[current] > STEP_ORDER[prevStep];
			tick().then(() => {
				killTweens(stepTween);
				stepTween = animateWizardStep(el, forward);
			});
		}
		prevStep = current;
	});

	// Stagger service cards when services load
	let servicesTween: gsap.core.Tween | null = null;
	$effect(() => {
		if (!servicesLoading && services.length > 0 && step === 'service') {
			tick().then(() => {
				killTweens(servicesTween);
				const cards = document.querySelectorAll('.service-card');
				if (cards.length) servicesTween = animateStaggerIn(cards);
			});
		}
	});

	// Stagger slot cards when slots load
	let slotsTween: gsap.core.Tween | null = null;
	$effect(() => {
		if (!slotsLoading && filteredSlots.length > 0) {
			tick().then(() => {
				killTweens(slotsTween);
				const cards = document.querySelectorAll('.slot-card');
				if (cards.length) slotsTween = animateStaggerIn(cards);
			});
		}
	});
</script>

<div class="page-wrapper flex flex-col gap-6">
	<a href="/coaches" class="back-link">← Back to coaches</a>

	{#if coachProfile}
		<ProfileCard
			name={coachProfile.name}
			specialty={coachProfile.specialty || undefined}
			description={coachProfile.description ?? undefined}
			pictureUrl={coachProfile.pictureUrl ?? undefined}
			socialLinks={coachProfile.socialLinks}
		/>
	{/if}

	<div>
		<p class="section-label">COACH #{coachId}</p>
		<h1 class="page-title">Book a session</h1>
	</div>

	<!-- Step indicator -->
	{#if !servicesLoading && !servicesError}
		<div class="flex items-center gap-3 mono text-[0.65rem] uppercase tracking-widest">
			{#each ['01 // SERVICE', '02 // SLOTS', '03 // CONFIRM'] as label, i}
				{#if i > 0}
					<span class="text-text-muted">→</span>
				{/if}
				<span
					class:text-primary={stepIndex === i}
					class:text-shadow-primary-sm={stepIndex === i}
					class:text-text-secondary={stepIndex !== i}
					style:opacity={stepIndex < i ? 0.4 : 1}
				>{label}</span>
			{/each}
		</div>
	{/if}

	{#if servicesLoading}
		<p class="loading-text">Loading…</p>
	{:else if servicesError}
		<p class="error-box">{servicesError}</p>
	{:else}

		<!-- STEP 1: SERVICE SELECTION -->
		{#if step === 'service'}
			<div bind:this={stepEl} class="flex flex-col gap-6">
			<div>
				<p class="section-label">STEP 01 // SELECT SERVICE</p>
			</div>

			<div class="flex flex-col gap-3">
				{#each services as svc}
					<button onclick={() => selectService(svc)} class="service-card">
						<div class="flex flex-col gap-1 flex-1 min-w-0">
							<span class="text-base font-semibold text-text-primary">{svc.name}</span>
							{#if servicePriceLabels.get(svc.id)}
								<span class="mono text-[0.7rem] text-primary">{servicePriceLabels.get(svc.id)}</span>
							{/if}
							{#if svc.description}
								<span class="text-sm text-text-secondary leading-snug">{svc.description}</span>
							{/if}
							{#if svc.extras.length > 0}
								<div class="flex flex-wrap gap-1.5 mt-2">
									{#each svc.extras as extra}
										<span class="slot-status slot-available">{extra.name}</span>
									{/each}
								</div>
							{/if}
						</div>
						<span class="service-card-arrow">→</span>
					</button>
				{/each}

				<button onclick={() => selectService(null)} class="service-card service-card-ghost">
					<div class="flex flex-col gap-1 flex-1 min-w-0">
						<span class="text-base font-semibold text-text-secondary">No preference</span>
						<span class="text-sm text-text-muted">Show all available time slots</span>
					</div>
					<span class="service-card-arrow">→</span>
				</button>
			</div>

		</div>
		<!-- STEP 2: DATE + SLOTS -->
		{:else if step === 'slots'}
			<div bind:this={stepEl} class="flex flex-col gap-6">
			<div>
				<p class="section-label">STEP 02 // SELECT DATE &amp; SLOTS</p>
			</div>

			{#if services.length > 0}
				<div class="panel panel-accent flex items-center justify-between gap-4">
					<div class="flex flex-col gap-0.5">
						<span class="mono text-[0.65rem] uppercase tracking-widest text-text-muted">Selected service</span>
						<span class="text-sm font-semibold text-text-primary">
							{selectedService !== null ? selectedService.name : 'No preference'}
						</span>
					</div>
					<button onclick={() => (step = 'service')} class="btn btn-secondary btn-sm shrink-0">← Change</button>
				</div>
			{/if}

			<div class="panel flex flex-col gap-4">
				<label class="form-label" for="slot-date">Select a date</label>
				<BookingCalendar
					bind:value={selectedDate}
					{coachId}
					serviceId={selectedService?.id ?? null}
					min={new Date().toISOString().slice(0, 10)}
					{timeWindows}
				/>
				<div class="flex flex-col gap-1">
					<span class="mono text-[0.6rem] uppercase tracking-widest text-text-muted">Or type a date</span>
					<DateInput id="slot-date" bind:value={selectedDate} min={new Date().toISOString().slice(0, 10)} />
				</div>
			</div>

			{#if selectedDate}
				{#if slotsLoading}
					<p class="loading-text">Loading slots…</p>
				{:else if slotsError}
					<p class="error-box">{slotsError}</p>
				{:else if filteredSlots.length === 0}
					<p class="empty-text">No slots available on this date.</p>
				{:else}
					<div class="panel flex flex-col gap-4">
						{#if sessionPriceLabel}
							<p class="mono text-[0.75rem] text-primary">{sessionPriceLabel}</p>
						{/if}

						<div class="flex flex-col gap-2">
							{#each filteredSlots as slot}
								{#if slot.status === 'AVAILABLE' && $role === 'CLIENT'}
									<button
										class="slot-card"
										class:selected={selectedSlotIds.has(slot.id)}
										onclick={() => toggleSlot(slot.id)}
										aria-label="Select slot {formatTime(slot.startsAt)}"
										aria-pressed={selectedSlotIds.has(slot.id)}
									>
										<span class="slot-card-time mono">
											{formatTime(slot.startsAt)}<span class="text-text-muted mx-2">→</span>{formatTime(slot.endsAt)}
										</span>
										{#if slot.serviceName}
											<span class="mono text-[0.65rem] text-text-muted uppercase tracking-widest">{slot.serviceName}</span>
										{/if}
										<span class="slot-check ml-auto">
											<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
												<polyline points="2,8 6,12 14,4" stroke="var(--color-primary)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
											</svg>
										</span>
									</button>
								{:else if slot.status === 'AVAILABLE' && !$role}
									<div class="slot-card slot-card-readonly">
										<span class="slot-card-time mono">
											{formatTime(slot.startsAt)}<span class="text-text-muted mx-2">→</span>{formatTime(slot.endsAt)}
										</span>
										<a href="/login" class="btn btn-secondary btn-sm ml-auto">Sign in to book</a>
									</div>
								{:else}
									<div class="slot-card slot-card-readonly opacity-50">
										<span class="mono text-sm text-text-muted">
											{formatTime(slot.startsAt)}<span class="mx-2">→</span>{formatTime(slot.endsAt)}
										</span>
										<span class="slot-status slot-booked ml-auto">{slot.status}</span>
									</div>
								{/if}
							{/each}
						</div>

						{#if $role === 'CLIENT'}
							<div class="flex items-center justify-between">
								<span class="text-xs text-text-muted mono">
									{selectedSlotIds.size} slot{selectedSlotIds.size !== 1 ? 's' : ''} selected
								</span>
								<button
									onclick={() => (step = 'confirm')}
									disabled={!canProceed}
									class="btn btn-primary"
									style:opacity={canProceed ? 1 : 0.4}
								>Continue →</button>
							</div>
						{/if}
					</div>
				{/if}
			{/if}

		</div>
		<!-- STEP 3: CONFIRM -->
		{:else if step === 'confirm'}
			<div bind:this={stepEl} class="flex flex-col gap-6">
			<div>
				<p class="section-label">STEP 03 // CONFIRM BOOKING</p>
			</div>

			<!-- Summary -->
			<div class="panel panel-accent flex flex-col gap-2">
				{#if selectedService !== null}
					<p class="detail-text"><span class="detail-label">Service:</span> {selectedService.name}</p>
				{/if}
				{#if sessionPriceLabel}
					<p class="detail-text"><span class="detail-label">Price per slot:</span> {sessionPriceLabel}</p>
				{/if}
				<p class="detail-text">
					<span class="detail-label">Slots selected:</span> {sortedSelectedSlots.length}
				</p>
				<div class="flex flex-col gap-1 mt-1">
					{#each sortedSelectedSlots as slot}
						<span class="mono text-[0.75rem] text-text-secondary">{formatDateTime(slot.startsAt)} → {formatTime(slot.endsAt)}</span>
					{/each}
				</div>
			</div>

			<!-- Extras -->
			{#if selectedService !== null && selectedService.extras.length > 0}
				<div>
					<p class="section-label">ADD-ONS // OPTIONAL</p>
				</div>
				<div class="panel flex flex-col gap-3">
					{#each selectedService.extras as extra}
						<label class="flex items-start gap-3 cursor-pointer">
							<input
								type="checkbox"
								checked={selectedExtraIds.has(extra.id)}
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
			{/if}

			<!-- Notes -->
			<div class="panel flex flex-col gap-2">
				<label class="form-label" for="notes">NOTES (OPTIONAL)</label>
				<textarea
					id="notes"
					bind:value={notes}
					rows="3"
					placeholder="Anything to share with your coach…"
					class="w-full px-3 py-2 text-sm"
				></textarea>
			</div>

			{#if bookingError}
				<p class="error-box">{bookingError}</p>
			{/if}

			<div class="flex gap-3">
				<button onclick={() => (step = 'slots')} class="btn btn-secondary">← Back</button>
				<button
					onclick={confirmBooking}
					disabled={bookingLoading}
					class="btn btn-primary btn-lg"
					style:opacity={bookingLoading ? 0.5 : 1}
				>{bookingLoading ? 'Booking…' : 'Confirm Booking'}</button>
			</div>
		</div>
		{/if}

	{/if}
</div>

<style>
	.slot-card {
		display: flex;
		align-items: center;
		gap: 12px;
		width: 100%;
		padding: 14px 18px;
		text-align: left;
		background: var(--color-surface-1);
		border: 1px solid var(--color-surface-border);
		border-left-width: 2px;
		border-left-color: transparent;
		cursor: pointer;
		transition: background 150ms ease, border-color 150ms ease, box-shadow 150ms ease;
	}
	.slot-card:hover:not(.selected) {
		border-color: rgba(26, 62, 207, 0.45);
		background: var(--color-surface-2);
	}
	.slot-card.selected {
		background: rgba(26, 62, 207, 0.1);
		border-color: var(--color-primary);
		border-left-color: var(--color-primary);
		box-shadow: var(--glow-primary);
	}
	.slot-card-readonly {
		cursor: default;
	}
	.slot-card-time {
		font-size: 0.9rem;
		color: var(--color-text-primary);
		font-weight: 500;
	}
	.slot-check {
		opacity: 0;
		transform: scale(0.4);
		transition: opacity 150ms ease, transform 200ms ease;
		flex-shrink: 0;
	}
	.slot-card.selected .slot-check {
		opacity: 1;
		transform: scale(1);
	}

	.service-card {
		display: flex;
		align-items: center;
		gap: 16px;
		width: 100%;
		text-align: left;
		padding: 20px 24px;
		background: var(--color-surface-1);
		border: 1px solid var(--color-surface-border);
		cursor: pointer;
		transition: border-color 150ms ease, box-shadow 150ms ease, background 150ms ease;
	}
	.service-card:hover {
		border-color: var(--color-primary);
		box-shadow: var(--glow-primary);
		background: var(--color-surface-2);
	}

	.service-card-ghost {
		border-style: dashed;
		opacity: 0.7;
	}
	.service-card-ghost:hover {
		opacity: 1;
	}

	.service-card-arrow {
		font-family: var(--font-mono);
		font-size: 1.1rem;
		color: var(--color-primary);
		flex-shrink: 0;
		transition: transform 150ms ease;
	}
	.service-card:hover .service-card-arrow {
		transform: translateX(4px);
	}
</style>
