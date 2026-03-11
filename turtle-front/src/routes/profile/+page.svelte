<script lang="ts">
	import { tick } from 'svelte';
	import {
		api,
		type SocialLinkType,
		type UpdateCoachProfileRequest,
		type UpdateClientProfileRequest
	} from '$lib/api';
	import { role, userId } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { animateStaggerIn, killTweens } from '$lib/gsap';

	const SOCIAL_TYPES: SocialLinkType[] = [
		'INSTAGRAM',
		'TWITTER',
		'LINKEDIN',
		'YOUTUBE',
		'TIKTOK',
		'FACEBOOK',
		'CUSTOM'
	];

	let loading = $state(true);
	let error = $state<string | null>(null);
	let saveError = $state<string | null>(null);
	let saving = $state(false);
	let success = $state(false);

	// Form fields
	let description = $state('');
	let specialty = $state('');
	let pictureUrl = $state('');
	let socialLinks = $state<{ type: SocialLinkType; url: string; label: string }[]>([]);
	let formEl = $state<HTMLElement>();
	let staggerTween: gsap.core.Tween | null = null;

	$effect(() => {
		if (!loading && !error && formEl) {
			tick().then(() => {
				killTweens(staggerTween);
				staggerTween = animateStaggerIn(':scope > *', formEl);
			});
		}
		return () => killTweens(staggerTween);
	});

	$effect(() => {
		if ($userId === null || $role === null) {
			goto('/login');
			return;
		}
		if ($role === 'COACH' || $role === 'COACH_PENDING') {
			api
				.getCoachProfile($userId)
				.then((p) => {
					description = p.description ?? '';
					specialty = p.specialty ?? '';
					pictureUrl = p.pictureUrl ?? '';
					socialLinks = p.socialLinks.map((l) => ({
						type: l.type,
						url: l.url,
						label: l.label ?? ''
					}));
				})
				.catch((e: { message?: string }) => (error = e.message ?? 'Failed to load profile'))
				.finally(() => (loading = false));
		} else if ($role === 'CLIENT') {
			api
				.getClientProfile($userId)
				.then((p) => {
					description = p.description ?? '';
					socialLinks = p.socialLinks.map((l) => ({
						type: l.type,
						url: l.url,
						label: l.label ?? ''
					}));
				})
				.catch((e: { message?: string }) => (error = e.message ?? 'Failed to load profile'))
				.finally(() => (loading = false));
		} else {
			loading = false;
		}
	});

	function addLink() {
		socialLinks = [...socialLinks, { type: 'CUSTOM', url: '', label: '' }];
	}

	function removeLink(index: number) {
		socialLinks = socialLinks.filter((_, i) => i !== index);
	}

	async function save(e: Event) {
		e.preventDefault();
		if ($userId === null) return;
		saving = true;
		saveError = null;
		success = false;
		try {
			if ($role === 'COACH' || $role === 'COACH_PENDING') {
				const payload: UpdateCoachProfileRequest = {
					description: description.trim() || undefined,
					specialty: specialty.trim() || undefined,
					pictureUrl: pictureUrl.trim() || undefined,
					socialLinks: socialLinks
						.filter((l) => l.url.trim())
						.map((l) => ({ type: l.type, url: l.url.trim(), label: l.label.trim() || undefined }))
				};
				await api.updateCoachProfile($userId, payload);
			} else if ($role === 'CLIENT') {
				const payload: UpdateClientProfileRequest = {
					description: description.trim() || undefined,
					socialLinks: socialLinks
						.filter((l) => l.url.trim())
						.map((l) => ({ type: l.type, url: l.url.trim(), label: l.label.trim() || undefined }))
				};
				await api.updateClientProfile($userId, payload);
			}
			success = true;
		} catch (err: unknown) {
			saveError = (err as { message?: string }).message ?? 'Failed to save profile';
		} finally {
			saving = false;
		}
	}
</script>

<div class="page-wrapper flex flex-col gap-6">
	<p class="section-label">ACCOUNT // MY PROFILE</p>
	<h1 class="page-title">Edit Profile</h1>

	{#if loading}
		<p class="loading-text">Loading…</p>
	{:else if error}
		<p class="error-box">{error}</p>
	{:else if $role === 'ADMIN'}
		<p class="empty-text">Admins do not have a profile to edit.</p>
	{:else}
		<form onsubmit={save} class="flex flex-col gap-6" bind:this={formEl}>

			<!-- Basic Info -->
			<div class="panel panel-accent flex flex-col gap-4">
				<p class="section-label">PROFILE // BASIC INFO</p>

				<div>
					<label class="form-label" for="desc">Bio / Description</label>
					<textarea
						id="desc"
						bind:value={description}
						rows="4"
						placeholder="Tell others about yourself…"
						class="w-full px-3 py-2 text-sm"
					></textarea>
				</div>

				{#if $role === 'COACH' || $role === 'COACH_PENDING'}
					<div>
						<label class="form-label" for="specialty">Specialty</label>
						<input
							id="specialty"
							type="text"
							bind:value={specialty}
							placeholder="e.g. Life Coaching, Career Coaching"
							class="h-10 w-full px-3 text-sm"
						/>
					</div>

					<div>
						<label class="form-label" for="picture">Profile Picture URL</label>
						<input
							id="picture"
							type="url"
							bind:value={pictureUrl}
							placeholder="https://…"
							class="h-10 w-full px-3 text-sm"
						/>
					</div>
				{/if}
			</div>

			<!-- Social Links -->
			<div class="panel flex flex-col gap-4">
				<div class="flex items-center justify-between">
					<p class="section-label" style:margin-bottom="0">PROFILE // SOCIAL LINKS</p>
					<button type="button" onclick={addLink} class="btn btn-secondary btn-sm">+ Add link</button>
				</div>

				{#if socialLinks.length === 0}
					<p class="empty-text text-sm">No social links added yet.</p>
				{/if}

				{#each socialLinks as link, i}
					<div class="flex items-center gap-3 flex-wrap">
						<select
							bind:value={socialLinks[i].type}
							class="h-10 px-2 text-sm shrink-0"
							style:width="130px"
						>
							{#each SOCIAL_TYPES as t}
								<option value={t}>{t}</option>
							{/each}
						</select>

						<input
							type="url"
							placeholder="https://…"
							bind:value={socialLinks[i].url}
							class="h-10 px-3 text-sm flex-1 min-w-0"
							style:min-width="180px"
						/>

						<input
							type="text"
							placeholder="Label (optional)"
							bind:value={socialLinks[i].label}
							class="h-10 px-3 text-sm shrink-0"
							style:width="160px"
						/>

						<button
							type="button"
							onclick={() => removeLink(i)}
							class="btn btn-sm btn-ghost-danger shrink-0"
						>✕</button>
					</div>
				{/each}
			</div>

			{#if saveError}
				<p class="error-box">{saveError}</p>
			{/if}

			{#if success}
				<p class="mono text-[0.75rem] tracking-wider" style:color="#4ade80">Profile saved successfully.</p>
			{/if}

			<div>
				<button
					type="submit"
					disabled={saving}
					class="btn btn-primary btn-lg"
					style:opacity={saving ? 0.5 : 1}
				>{saving ? 'Saving…' : 'Save Profile'}</button>
			</div>
		</form>
	{/if}
</div>
