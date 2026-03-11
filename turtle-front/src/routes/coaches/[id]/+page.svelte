<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { page } from '$app/state';
	import { api, type AvailabilitySlot, type CoachingServiceResponse, type TimeWindowResponse, type CoachProfile } from '$lib/api';
	import { role } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { DateInput, BookingCalendar, ProfileCard } from '$lib';
	import gsap from 'gsap';
	import { animateWizardStep, animateStaggerIn, killTweens, animateOpenSlotModal, animateCloseSlotModal } from '$lib/gsap';

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

	// Full-page slot panel
	let showSlotModal = $state(false);
	let modalEl = $state<HTMLElement | undefined>();
	let modalAnimating = $state(false);
	let scrollBodyEl = $state<HTMLElement | undefined>();
	// Plain vars for gesture tracking — no reactive overhead per frame
	let isDragging = false;
	let dragStartY = 0;
	let isPullMode = false;
	let touchStartY = 0;
	let wheelAccum = 0;
	let wheelResetTimer: ReturnType<typeof setTimeout> | null = null;

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
		if (!selectedDate || step === 'service') {
			slots = [];
			selectedSlotIds = new Set();
			return;
		}
		if (step !== 'slots') return; // on 'confirm' — preserve slots & selection
		selectedSlotIds = new Set();
		slotsLoading = true;
		slotsError = null;
		if (untrack(() => !showSlotModal)) showSlotModal = true;
		const fetch =
			selectedService !== null
				? api.getSlotsByService(selectedService.id, selectedDate)
				: api.getSlotsByDate(coachId, selectedDate);
		fetch
			.then((data) => { slots = data; })
			.catch((e: { message?: string }) => (slotsError = e.message ?? 'Failed to load slots'))
			.finally(() => (slotsLoading = false));
	});

	// Trigger open animation when panel mounts
	$effect(() => {
		if (showSlotModal && modalEl) {
			if (untrack(() => !modalAnimating)) {
				modalAnimating = true;
				animateOpenSlotModal(modalEl).then(() => { modalAnimating = false; });
			}
		}
	});

	// Auto-close modal when leaving step 2
	$effect(() => {
		if (step !== 'slots' && showSlotModal) showSlotModal = false;
	});

	// Lock body scroll while modal is open
	$effect(() => {
		document.body.style.overflow = showSlotModal ? 'hidden' : '';
		return () => { document.body.style.overflow = ''; };
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

	async function closeModal() {
		if (modalAnimating) return;
		if (modalEl) {
			modalAnimating = true;
			await animateCloseSlotModal(modalEl);
			modalAnimating = false;
		}
		showSlotModal = false;
		selectedDate = '';
	}

	async function continueFromModal() {
		if (modalAnimating || selectedSlotIds.size === 0) return;
		if (modalEl) {
			modalAnimating = true;
			await animateCloseSlotModal(modalEl);
			modalAnimating = false;
		}
		showSlotModal = false;
		step = 'confirm';
	}

	function dismissOrSnapBack(currentY: number) {
		if (!modalEl) return;
		if (currentY > window.innerHeight * 0.25) {
			closeModal();
		} else {
			gsap.to(modalEl, { y: 0, duration: 0.35, ease: 'power3.out' });
		}
	}

	function onDragStart(e: PointerEvent) {
		if (modalAnimating) return;
		isDragging = true;
		dragStartY = e.clientY;
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
	}

	function onDragMove(e: PointerEvent) {
		if (!isDragging || !modalEl) return;
		gsap.set(modalEl, { y: Math.max(0, e.clientY - dragStartY) });
	}

	function onDragEnd(e: PointerEvent) {
		if (!isDragging || !modalEl) return;
		isDragging = false;
		dismissOrSnapBack(Math.max(0, e.clientY - dragStartY));
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

	function formatDate(dt: string) {
		return new Date(dt).toLocaleDateString(undefined, { dateStyle: 'medium' });
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

	// Stagger slot cards when slots load inside modal
	let slotsTween: gsap.core.Tween | null = null;
	$effect(() => {
		if (!slotsLoading && filteredSlots.length > 0 && showSlotModal) {
			tick().then(() => {
				killTweens(slotsTween);
				const cards = document.querySelectorAll('.slot-card');
				if (cards.length) slotsTween = animateStaggerIn(cards);
			});
		}
	});

	// Touch overscroll-to-dismiss on modal scroll body
	$effect(() => {
		const el = scrollBodyEl;
		if (!el) return;

		function onTouchStart(e: TouchEvent) {
			touchStartY = e.touches[0].clientY;
			isPullMode = false;
		}

		function onTouchMove(e: TouchEvent) {
			if (!el || !modalEl || modalAnimating) return;
			const deltaY = e.touches[0].clientY - touchStartY;
			if (el.scrollTop === 0 && deltaY > 0) {
				e.preventDefault();
				isPullMode = true;
				gsap.set(modalEl, { y: deltaY });
			} else if (isPullMode && deltaY <= 0) {
				isPullMode = false;
				gsap.set(modalEl, { y: 0 });
			}
		}

		function onTouchEnd(e: TouchEvent) {
			if (!isPullMode) return;
			isPullMode = false;
			dismissOrSnapBack(Math.max(0, e.changedTouches[0].clientY - touchStartY));
		}

		function onWheel(e: WheelEvent) {
			if (!el || !modalEl || modalAnimating) return;
			if (el.scrollTop === 0 && e.deltaY < 0) {
				e.preventDefault();
				wheelAccum += Math.abs(e.deltaY);
				const dragY = Math.min(wheelAccum * 0.8, window.innerHeight * 0.5);
				gsap.set(modalEl, { y: dragY });

				if (wheelResetTimer) clearTimeout(wheelResetTimer);

				if (dragY > window.innerHeight * 0.10) {
					wheelAccum = 0;
					closeModal();
					return;
				}

				wheelResetTimer = setTimeout(() => {
					wheelAccum = 0;
					if (!modalAnimating) gsap.to(modalEl!, { y: 0, duration: 0.35, ease: 'power3.out' });
				}, 200);
			} else if (wheelAccum > 0) {
				wheelAccum = 0;
				if (wheelResetTimer) clearTimeout(wheelResetTimer);
				gsap.to(modalEl, { y: 0, duration: 0.2, ease: 'power2.out' });
			}
		}

		el.addEventListener('touchstart', onTouchStart, { passive: true });
		el.addEventListener('touchmove', onTouchMove, { passive: false });
		el.addEventListener('touchend', onTouchEnd, { passive: true });
		el.addEventListener('wheel', onWheel, { passive: false });

		return () => {
			el.removeEventListener('touchstart', onTouchStart);
			el.removeEventListener('touchmove', onTouchMove);
			el.removeEventListener('touchend', onTouchEnd);
			el.removeEventListener('wheel', onWheel);
			if (wheelResetTimer) clearTimeout(wheelResetTimer);
		};
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

			{#if !selectedDate}
				<p class="empty-text mono text-[0.7rem] uppercase tracking-widest text-center py-4">
					Select a date above to view available slots
				</p>
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
				<div class="flex flex-col gap-2 mt-2">
					{#each sortedSelectedSlots as slot}
						<div class="confirm-slot-row">
							<div class="flex flex-col gap-0.5 flex-1 min-w-0">
								<span class="confirm-slot-time mono">
									{formatTime(slot.startsAt)}<span class="text-text-muted mx-2">→</span>{formatTime(slot.endsAt)}
								</span>
								<span class="mono text-[0.6rem] uppercase tracking-widest text-text-muted">{formatDate(slot.startsAt)}</span>
							</div>
							{#if slot.serviceName}
								<span class="confirm-slot-service">{slot.serviceName}</span>
							{/if}
						</div>
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

		{#if showSlotModal}
		<!-- Full-page slot panel -->
		<div
			bind:this={modalEl}
			class="fixed inset-0 z-[110] bg-surface-1 flex flex-col"
			role="dialog"
			aria-modal="true"
			aria-label="Available slots"
			tabindex="-1"
			onclick={(e) => e.stopPropagation()}
			onkeydown={(e) => e.stopPropagation()}
		>
			<!-- Drag handle -->
			<div
				class="drag-handle shrink-0"
				onpointerdown={onDragStart}
				onpointermove={onDragMove}
				onpointerup={onDragEnd}
				onpointercancel={onDragEnd}
				role="button"
				tabindex="0"
				aria-label="Drag down to dismiss"
				onkeydown={(e) => { if (e.key === 'Escape') closeModal(); }}
			>
				<div class="drag-bar"></div>
				<p class="drag-hint">↓ DRAG DOWN TO GO BACK</p>
			</div>

			<!-- Header -->
			<div class="flex items-center justify-between px-6 py-4 border-b border-surface-border shrink-0">
				<div class="flex flex-col gap-1">
					<p class="mono text-xl font-semibold uppercase tracking-widest text-text-primary">SLOTS // {selectedDate}</p>
					{#if !slotsLoading}
						<p class="mono text-sm uppercase tracking-widest text-text-muted">
							{filteredSlots.filter(s => s.status === 'AVAILABLE').length} available
							{#if selectedSlotIds.size > 0}— {selectedSlotIds.size} selected{/if}
						</p>
					{/if}
				</div>
				<button onclick={closeModal} class="btn btn-secondary" disabled={modalAnimating}>
					← BACK
				</button>
			</div>

			<!-- Scrollable body -->
			<div bind:this={scrollBodyEl} class="overflow-y-auto overscroll-contain flex-1 p-6 flex flex-col gap-3">
				{#if slotsLoading}
					<p class="loading-text">Loading slots…</p>
				{:else if slotsError}
					<p class="error-box">{slotsError}</p>
				{:else if filteredSlots.length === 0}
					<p class="empty-text">No slots available on this date.</p>
				{:else}
					{#if sessionPriceLabel}
						<p class="mono text-[0.9rem] text-primary mb-3">{sessionPriceLabel}</p>
					{/if}
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
									<span class="mono text-[0.75rem] text-text-muted uppercase tracking-widest">{slot.serviceName}</span>
								{/if}
								<span class="slot-check ml-auto">
									<svg width="20" height="20" viewBox="0 0 16 16" fill="none" aria-hidden="true">
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
								<span class="mono text-base text-text-muted">
									{formatTime(slot.startsAt)}<span class="mx-2">→</span>{formatTime(slot.endsAt)}
								</span>
								<span class="slot-status slot-booked ml-auto">{slot.status}</span>
							</div>
						{/if}
					{/each}
				{/if}
			</div>

			<!-- Footer -->
			{#if $role === 'CLIENT'}
				<div class="px-6 py-5 border-t border-surface-border shrink-0">
					<button
						class="btn btn-primary btn-lg w-full text-lg"
						disabled={selectedSlotIds.size === 0 || modalAnimating}
						style:opacity={selectedSlotIds.size > 0 && !modalAnimating ? 1 : 0.4}
						onclick={continueFromModal}
					>
						{#if selectedSlotIds.size > 0}
							CONTINUE → {selectedSlotIds.size} SLOT{selectedSlotIds.size !== 1 ? 'S' : ''} SELECTED
						{:else}
							SELECT A SLOT TO CONTINUE
						{/if}
					</button>
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.drag-handle {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding: 12px 24px 10px;
		cursor: grab;
		user-select: none;
		touch-action: none;
	}
	.drag-handle:active {
		cursor: grabbing;
	}
	.drag-bar {
		width: 36px;
		height: 4px;
		background: var(--color-surface-border);
	}
	.drag-hint {
		font-family: var(--font-mono);
		font-size: 0.6rem;
		letter-spacing: 0.18em;
		color: var(--color-text-muted);
		text-transform: uppercase;
	}

	.slot-card {
		display: flex;
		align-items: center;
		gap: 12px;
		width: 100%;
		padding: 18px 24px;
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
		font-size: 1.15rem;
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

	.confirm-slot-row {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 16px;
		background: var(--color-surface-2);
		border: 1px solid var(--color-surface-border);
		border-left: 2px solid var(--color-primary);
	}

	.confirm-slot-time {
		font-size: 0.9rem;
		color: var(--color-text-primary);
		font-weight: 500;
	}

	.confirm-slot-service {
		font-family: var(--font-mono);
		font-size: 0.6rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--color-primary);
		background: rgba(26, 62, 207, 0.12);
		border: 1px solid rgba(26, 62, 207, 0.3);
		padding: 3px 8px;
		white-space: nowrap;
		flex-shrink: 0;
	}
	.service-card:hover .service-card-arrow {
		transform: translateX(4px);
	}
</style>
