<script lang="ts">
	import { tick } from 'svelte';
	import { page } from '$app/state';
	import { api, type BookingResponse, type BookingResourceResponse, type ChatMessage, type CoachProfile, type ClientProfile } from '$lib/api';
	import { role, userId } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { statusBadgeClass, statusLabel } from '$lib/status';
	import { ProfileCard } from '$lib';
	import { animateSuccess, animateShake, animatePulse, animateGlowFlash, animateSlideUp, animateStaggerIn, killTweens } from '$lib/gsap';

	const bookingId = Number(page.params.id);

	let booking = $state<BookingResponse | null>(null);
	let counterpartProfile = $state<CoachProfile | ClientProfile | null>(null);
	let messages = $state<ChatMessage[]>([]);
	let resources = $state<BookingResourceResponse[]>([]);
	let loadingBooking = $state(true);
	let loadingMessages = $state(false);
	let error = $state('');
	let actionError = $state('');
	let newMessage = $state('');
	let sendingMessage = $state(false);
	let paymentLoading = $state(false);
	let paymentError = $state('');
	let paymentBanner = $state<'success' | 'failed' | 'pending' | null>(null);
	let justCreated = $state(false);

	let showResourceForm = $state(false);
	let resourceTitle = $state('');
	let resourceUrl = $state('');
	let resourceDescription = $state('');
	let addingResource = $state(false);
	let resourceError = $state('');
	let deletingResourceId = $state<number | null>(null);

	// Animation refs
	let wrapperEl: HTMLElement;
	let bannerTween: gsap.core.Tween | gsap.core.Timeline | null = null;
	let prevStatus: string | null = null;
	let prevMsgCount = 0;
	let prevResourceCount = 0;

	// Payment banner animations
	$effect(() => {
		if (paymentBanner && wrapperEl) {
			tick().then(() => {
				killTweens(bannerTween);
				if (paymentBanner === 'success') {
					const el = wrapperEl.querySelector('.panel.panel-accent') as HTMLElement | null;
					if (el) bannerTween = animateSuccess(el);
				} else if (paymentBanner === 'failed') {
					const el = wrapperEl.querySelector('.error-box') as HTMLElement | null;
					if (el) bannerTween = animateShake(el);
				} else if (paymentBanner === 'pending') {
					const el = wrapperEl.querySelector('.panel') as HTMLElement | null;
					if (el) bannerTween = animatePulse(el);
				}
			});
		}
	});

	// Status badge glow on change
	$effect(() => {
		const status = booking?.status;
		if (status && prevStatus !== null && status !== prevStatus && wrapperEl) {
			tick().then(() => {
				const badge = wrapperEl.querySelector('.status-badge') as HTMLElement | null;
				if (badge) {
					const glowMap: Record<string, string> = {
						CONFIRMED: '0 0 12px rgba(26,122,60,0.8), 0 0 32px rgba(26,122,60,0.3)',
						REJECTED: '0 0 12px rgba(192,21,44,0.8), 0 0 32px rgba(192,21,44,0.3)',
						CANCELLED: '0 0 12px rgba(90,90,98,0.6), 0 0 24px rgba(90,90,98,0.2)'
					};
					animateGlowFlash(badge, glowMap[status] ?? '0 0 12px rgba(26,62,207,0.8), 0 0 32px rgba(26,62,207,0.3)');
				}
			});
		}
		prevStatus = status ?? null;
	});

	// Chat message slide-in
	$effect(() => {
		const count = messages.length;
		if (count > prevMsgCount && prevMsgCount > 0 && wrapperEl) {
			tick().then(() => {
				const items = wrapperEl.querySelectorAll('.chat-list > li');
				const last = items[items.length - 1] as HTMLElement | null;
				if (last) animateSlideUp(last);
			});
		}
		prevMsgCount = count;
	});

	// Resource add slide-in
	$effect(() => {
		const count = resources.length;
		if (count > prevResourceCount && prevResourceCount > 0 && wrapperEl) {
			tick().then(() => {
				const items = wrapperEl.querySelectorAll('.resource-item');
				const last = items[items.length - 1] as HTMLElement | null;
				if (last) animateSlideUp(last);
			});
		}
		prevResourceCount = count;
	});

	// Stagger entrance for main content
	$effect(() => {
		if (!loadingBooking && booking && wrapperEl) {
			tick().then(() => {
				animateStaggerIn(':scope > *', wrapperEl);
			});
		}
	});

	$effect(() => {
		const paymentResult = page.url.searchParams.get('payment');
		if (paymentResult === 'success' || paymentResult === 'failed' || paymentResult === 'pending') {
			paymentBanner = paymentResult as 'success' | 'failed' | 'pending';
		}
		if (page.url.searchParams.get('action') === 'pay') {
			justCreated = true;
		}
		if (paymentResult || page.url.searchParams.get('action')) {
			goto(`/bookings/${bookingId}`, { replaceState: true });
		}

		api.getBooking(bookingId)
			.then((b) => {
				booking = b;
				resources = b.resources ?? [];
				if (b.status === 'CONFIRMED') {
					loadingMessages = true;
					return api.getMessages(bookingId).then((m) => (messages = m));
				}
			})
			.catch(() => (error = 'Failed to load booking'))
			.finally(() => {
				loadingBooking = false;
				loadingMessages = false;
			});
	});

	$effect(() => {
		if (!booking) return;
		if ($role === 'CLIENT') {
			api.getCoachProfile(booking.coachId)
				.then((p) => (counterpartProfile = p))
				.catch(() => {});
		} else if ($role === 'COACH') {
			api.getClientProfile(booking.clientId)
				.then((p) => (counterpartProfile = p))
				.catch(() => {});
		}
	});

	async function confirm() {
		if (!booking) return;
		actionError = '';
		try {
			booking = await api.confirmBooking(bookingId);
			messages = await api.getMessages(bookingId);
		} catch (err: unknown) {
			actionError = (err as { message?: string }).message ?? 'Failed';
		}
	}

	async function initiatePayment() {
		if (!booking) return;
		paymentLoading = true;
		paymentError = '';
		try {
			const result = await api.createPaymentPreference(bookingId);
			if (result.free) {
				booking = await api.getBooking(bookingId);
			} else {
				window.location.href = result.checkoutUrl;
			}
		} catch (err: unknown) {
			paymentError = (err as { message?: string }).message ?? 'Payment initiation failed';
		} finally {
			paymentLoading = false;
		}
	}

	async function reject() {
		if (!booking) return;
		actionError = '';
		try {
			booking = await api.rejectBooking(bookingId);
		} catch (err: unknown) {
			actionError = (err as { message?: string }).message ?? 'Failed';
		}
	}

	async function cancel() {
		actionError = '';
		try {
			await api.cancelBooking(bookingId);
			goto('/bookings');
		} catch (err: unknown) {
			actionError = (err as { message?: string }).message ?? 'Failed';
		}
	}

	async function send(e: Event) {
		e.preventDefault();
		if (!newMessage.trim()) return;
		sendingMessage = true;
		try {
			const msg = await api.sendMessage(bookingId, newMessage.trim());
			messages = [...messages, msg];
			newMessage = '';
		} catch (err: unknown) {
			actionError = (err as { message?: string }).message ?? 'Failed to send';
		} finally {
			sendingMessage = false;
		}
	}

	async function addResource(e: Event) {
		e.preventDefault();
		if (!resourceTitle.trim() || !resourceUrl.trim()) return;
		addingResource = true;
		resourceError = '';
		try {
			const res = await api.addBookingResource(bookingId, {
				title: resourceTitle.trim(),
				url: resourceUrl.trim(),
				description: resourceDescription.trim() || undefined
			});
			resources = [...resources, res];
			resourceTitle = '';
			resourceUrl = '';
			resourceDescription = '';
			showResourceForm = false;
		} catch (err: unknown) {
			resourceError = (err as { message?: string }).message ?? 'Failed to add resource';
		} finally {
			addingResource = false;
		}
	}

	async function deleteResource(resourceId: number) {
		deletingResourceId = resourceId;
		resourceError = '';
		try {
			await api.deleteBookingResource(bookingId, resourceId);
			resources = resources.filter((r) => r.id !== resourceId);
		} catch (err: unknown) {
			resourceError = (err as { message?: string }).message ?? 'Failed to delete resource';
		} finally {
			deletingResourceId = null;
		}
	}

	function extractDomain(url: string): string {
		try {
			return new URL(url).hostname.replace('www.', '');
		} catch {
			return url;
		}
	}

	function fmt(dt: string) {
		return new Date(dt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
	}
</script>

<div class="page-wrapper flex flex-col gap-6" bind:this={wrapperEl}>
	<a href="/bookings" class="back-link">← Back to bookings</a>

	{#if paymentBanner === 'success'}
		<div class="panel panel-accent">
			<p class="section-label mb-1">PAYMENT // CONFIRMED</p>
			<p class="text-sm text-[#4ade80]">Payment received successfully. Your booking is now awaiting coach confirmation.</p>
		</div>
	{:else if paymentBanner === 'failed'}
		<p class="error-box">Payment failed or was declined by MercadoPago. You can try again below.</p>
	{:else if paymentBanner === 'pending'}
		<div class="panel">
			<p class="section-label mb-1">PAYMENT // PENDING</p>
			<p class="text-sm text-[#f0a840]">Your payment is under review by MercadoPago. Your booking status will update once payment is confirmed.</p>
		</div>
	{/if}

	{#if loadingBooking}
		<p class="loading-text">Loading…</p>
	{:else if error}
		<p class="error-box">{error}</p>
	{:else if booking}
		<div class="panel panel-accent">
			<div class="flex items-center justify-between mb-4">
				<div>
					<p class="section-label mb-1">BOOKING #{booking.id}</p>
					<h1 class="page-title-md">Session Detail</h1>
				</div>
				<span class="status-badge {statusBadgeClass[booking.status]}">{statusLabel[booking.status] ?? booking.status}</span>
			</div>

			<div class="flex flex-col gap-1.5 mb-4">
				<p class="detail-text"><span class="detail-label">Coach:</span> {booking.coachName}</p>
				<p class="detail-text"><span class="detail-label">Client:</span> {booking.clientName}</p>
				<p class="detail-text"><span class="detail-label">Session:</span> {fmt(booking.startsAt)} – {new Date(booking.endsAt).toLocaleTimeString(undefined, { timeStyle: 'short' })}</p>
				{#if booking.notes}<p class="detail-text"><span class="detail-label">Notes:</span> {booking.notes}</p>{/if}
			{#if booking.paymentStatus}
				<p class="detail-text"><span class="detail-label">Payment:</span> <span class="mono text-[0.7rem] uppercase">{booking.paymentStatus}</span></p>
			{/if}
				{#if booking.extras && booking.extras.length > 0}
					<div class="mt-2 pt-2 border-t border-surface-border">
						<p class="detail-label mb-1.5">Add-ons</p>
						<ul class="flex flex-col gap-1 list-none p-0 m-0">
							{#each booking.extras as extra}
								<li class="flex flex-col">
									<span class="text-sm font-semibold text-text-primary">{extra.name}</span>
									{#if extra.description}
										<span class="text-xs text-text-secondary">{extra.description}</span>
									{/if}
								</li>
							{/each}
						</ul>
					</div>
				{/if}
			</div>

			{#if actionError}
				<p class="error-box mb-4">{actionError}</p>
			{/if}

			{#if $role === 'COACH' && booking.status === 'AWAITING_COACH'}
				<div class="flex gap-3">
					<button onclick={confirm} class="btn btn-success">Confirm Session</button>
					<button onclick={reject} class="btn btn-danger">Reject</button>
				</div>
			{/if}

			{#if $role === 'CLIENT'}
				{#if booking.status === 'PENDING_PAYMENT'}
					{#if justCreated}
						<div class="panel panel-accent mb-3">
							<p class="section-label mb-1">NEXT STEP // PAYMENT REQUIRED</p>
							<p class="text-sm text-text-secondary">Your booking is reserved. Complete payment below to confirm your session with the coach.</p>
						</div>
					{/if}
					{#if paymentError}
						<p class="error-box mb-3">{paymentError}</p>
					{/if}
					<div class="flex gap-3 items-center">
						<button
							onclick={initiatePayment}
							disabled={paymentLoading}
							class="btn btn-primary btn-lg"
							style:opacity={paymentLoading ? 0.5 : 1}
						>{paymentLoading ? 'Redirecting…' : 'Pay Now'}</button>
						<button onclick={cancel} class="btn btn-ghost-danger">Cancel booking</button>
					</div>
				{:else if booking.status === 'AWAITING_COACH'}
					<div class="flex flex-col gap-2">
						<p class="mono text-[0.7rem] text-text-muted uppercase tracking-widest">Payment received. Awaiting coach confirmation.</p>
						<div>
							<button onclick={cancel} class="btn btn-ghost-danger">Cancel booking</button>
						</div>
					</div>
				{/if}
			{/if}
		</div>

		{#if counterpartProfile}
		<div>
			<p class="section-label">{$role === 'CLIENT' ? 'COACH // PROFILE' : 'CLIENT // PROFILE'}</p>
			<ProfileCard
				name={counterpartProfile.name}
				description={counterpartProfile.description ?? undefined}
				specialty={'specialty' in counterpartProfile ? counterpartProfile.specialty || undefined : undefined}
				pictureUrl={'pictureUrl' in counterpartProfile ? counterpartProfile.pictureUrl ?? undefined : undefined}
				socialLinks={counterpartProfile.socialLinks}
			/>
		</div>
	{/if}

	{#if booking.status === 'AWAITING_COACH' || booking.status === 'CONFIRMED'}
		<div class="panel flex flex-col gap-4">
			<div class="flex items-center justify-between">
				<div>
					<p class="section-label">RESOURCES // SESSION MATERIALS</p>
					<h2 class="panel-title">Resources</h2>
				</div>
				{#if $role === 'COACH' && !showResourceForm}
					<button onclick={() => (showResourceForm = true)} class="btn btn-primary btn-sm">
						+ Add Resource
					</button>
				{/if}
			</div>

			{#if resourceError}
				<p class="error-box">{resourceError}</p>
			{/if}

			{#if $role === 'COACH' && showResourceForm}
				<form onsubmit={addResource} class="flex flex-col gap-3 border border-surface-border bg-surface-2 p-4">
					<div class="flex items-center justify-between">
						<p class="section-heading m-0">New Resource</p>
						<button type="button" onclick={() => (showResourceForm = false)} class="btn btn-sm btn-secondary">Cancel</button>
					</div>
					<div>
						<label for="res-title" class="form-label">Title</label>
						<input id="res-title" type="text" bind:value={resourceTitle} required class="h-10 w-full px-3 text-sm" placeholder="e.g. Session recording" />
					</div>
					<div>
						<label for="res-url" class="form-label">URL</label>
						<input id="res-url" type="url" bind:value={resourceUrl} required class="h-10 w-full px-3 text-sm" placeholder="https://" />
					</div>
					<div>
						<label for="res-desc" class="form-label">Description <span class="text-text-muted">(optional)</span></label>
						<textarea id="res-desc" bind:value={resourceDescription} rows="2" class="w-full px-3 py-2 text-sm"></textarea>
					</div>
					<div class="flex justify-end">
						<button type="submit" disabled={addingResource} class="btn btn-primary" style:opacity={addingResource ? 0.5 : 1}>
							{addingResource ? 'Adding…' : 'Add Resource'}
						</button>
					</div>
				</form>
			{/if}

			{#if resources.length === 0}
				<p class="empty-text text-sm">{$role === 'COACH' ? 'No resources added yet. Share links and materials with your client.' : 'No resources shared yet.'}</p>
			{:else}
				<ul class="flex flex-col gap-2 list-none p-0 m-0">
					{#each resources as resource (resource.id)}
						<li class="resource-item flex items-start gap-3 border border-surface-border bg-surface-2 px-4 py-3 transition-[border-color] duration-150 hover:border-primary/45">
							<div class="flex-1 min-w-0">
								<a href={resource.url} target="_blank" rel="noopener noreferrer" class="text-sm font-semibold text-primary hover:text-primary-light transition-colors duration-150">
									{resource.title}
									<span class="mono text-[0.6rem] text-text-muted ml-1.5">↗</span>
								</a>
								<p class="mono text-[0.6rem] text-text-muted mt-0.5 truncate">{extractDomain(resource.url)}</p>
								{#if resource.description}
									<p class="text-xs text-text-secondary mt-1">{resource.description}</p>
								{/if}
							</div>
							<div class="flex items-center gap-3 shrink-0 pt-0.5">
								<span class="mono text-[0.6rem] text-text-muted">{fmt(resource.createdAt)}</span>
								{#if $role === 'COACH'}
									<button
										onclick={() => deleteResource(resource.id)}
										disabled={deletingResourceId === resource.id}
										class="btn btn-ghost-danger btn-sm"
										style:opacity={deletingResourceId === resource.id ? 0.5 : 1}
									>Delete</button>
								{/if}
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</div>
	{/if}

	{#if booking.status === 'CONFIRMED'}
			<div class="panel flex flex-col gap-4">
				<p class="section-label">COMMS // CHAT</p>
				<h2 class="panel-title">Chat</h2>

				{#if loadingMessages}
					<p class="loading-text">Loading messages…</p>
				{:else}
					<ul class="chat-list">
						{#each messages as msg}
							<li
								class:chat-msg-own={msg.senderId === $userId}
								class:chat-msg-other={msg.senderId !== $userId}
							>
								<span class="chat-meta mb-1">{msg.senderName}</span>
								<div
									class:chat-bubble-own={msg.senderId === $userId}
									class:chat-bubble-other={msg.senderId !== $userId}
								>{msg.content}</div>
								<span class="chat-meta mt-1">{fmt(msg.sentAt)}</span>
							</li>
						{/each}
						{#if messages.length === 0}
							<p class="text-sm empty-text">No messages yet. Say hello!</p>
						{/if}
					</ul>

					<form onsubmit={send} class="flex gap-2">
						<input
							type="text"
							bind:value={newMessage}
							placeholder="Type a message…"
							class="h-10 flex-1 px-3 text-sm"
						/>
						<button
							type="submit"
							disabled={sendingMessage}
							class="btn btn-primary h-10 px-[18px] py-0"
							style:opacity={sendingMessage ? 0.5 : 1}
						>Send</button>
					</form>
				{/if}
			</div>
		{/if}
	{/if}
</div>
