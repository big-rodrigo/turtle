<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { api } from '$lib/api';
	import { token } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { animateScaleIn, animateShake, killTweens } from '$lib/gsap';

	let name = $state('');
	let email = $state('');
	let phone = $state('');
	let password = $state('');
	let roleChoice = $state<'CLIENT' | 'COACH'>('CLIENT');
	let errors = $state<string[]>([]);
	let errorMsg = $state('');
	let loading = $state(false);
	let panelEl: HTMLElement;
	let shakeTween: gsap.core.Tween | null = null;

	onMount(() => {
		const tween = animateScaleIn(panelEl);
		return () => killTweens(tween, shakeTween);
	});

	$effect(() => {
		if (errorMsg && panelEl) {
			tick().then(() => {
				const errorEl = panelEl.querySelector('.error-box') as HTMLElement | null;
				if (errorEl) {
					killTweens(shakeTween);
					shakeTween = animateShake(errorEl);
				}
			});
		}
	});

	async function submit(e: Event) {
		e.preventDefault();
		errors = [];
		errorMsg = '';
		loading = true;
		try {
			const res = await api.register({
				name,
				email,
				phone: phone || undefined,
				password,
				role: roleChoice
			});
			token.login(res.token);
			goto('/');
		} catch (err: unknown) {
			const apiErr = err as { message?: string; errors?: string[] };
			errorMsg = apiErr.message ?? 'Registration failed';
			errors = apiErr.errors ?? [];
		} finally {
			loading = false;
		}
	}
</script>

<div class="auth-centered">
	<div class="panel panel-accent w-full max-w-[420px]" bind:this={panelEl}>
		<p class="section-label mb-4">AUTH // CREATE ACCOUNT</p>
		<h1 class="page-title-md mb-6">Create account</h1>

		{#if errorMsg}
			<div class="error-box mb-4">
				<p>{errorMsg}</p>
				{#if errors.length}
					<ul class="mt-1.5 pl-4 list-disc">
						{#each errors as e}<li>{e}</li>{/each}
					</ul>
				{/if}
			</div>
		{/if}

		<form onsubmit={submit} class="flex flex-col gap-4">
			<div>
				<label class="form-label" for="name">Full name</label>
				<input id="name" type="text" bind:value={name} required class="h-10 w-full px-3 text-sm" />
			</div>
			<div>
				<label class="form-label" for="email">Email</label>
				<input id="email" type="email" bind:value={email} required class="h-10 w-full px-3 text-sm" />
			</div>
			<div>
				<label class="form-label" for="phone">
					Phone <span class="text-text-muted normal-case">(optional)</span>
				</label>
				<input id="phone" type="tel" bind:value={phone} class="h-10 w-full px-3 text-sm" />
			</div>
			<div>
				<label class="form-label" for="password">Password</label>
				<input id="password" type="password" bind:value={password} required minlength={6} class="h-10 w-full px-3 text-sm" />
			</div>
			<div>
				<p class="form-label mb-[10px]">I am a…</p>
				<div class="flex gap-6">
					<label class="flex items-center gap-2 text-sm text-text-primary cursor-pointer">
						<input type="radio" bind:group={roleChoice} value="CLIENT" />
						Client
					</label>
					<label class="flex items-center gap-2 text-sm text-text-primary cursor-pointer">
						<input type="radio" bind:group={roleChoice} value="COACH" />
						Coach
					</label>
				</div>
			</div>
			<button
				type="submit"
				disabled={loading}
				class="btn btn-primary w-full py-[10px]"
				style:opacity={loading ? 0.5 : 1}
			>
				{loading ? 'Creating account…' : 'Create account'}
			</button>
		</form>

		<p class="mt-5 text-center text-sm text-text-secondary">
			Already have an account? <a href="/login" class="text-primary font-semibold">Sign in</a>
		</p>
	</div>
</div>
