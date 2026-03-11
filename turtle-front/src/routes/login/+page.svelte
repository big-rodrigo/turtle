<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { api } from '$lib/api';
	import { token } from '$lib/auth';
	import { goto } from '$app/navigation';
	import { animateScaleIn, animateShake, killTweens } from '$lib/gsap';

	let email = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);
	let panelEl: HTMLElement;
	let shakeTween: gsap.core.Tween | null = null;

	onMount(() => {
		const tween = animateScaleIn(panelEl);
		return () => killTweens(tween, shakeTween);
	});

	$effect(() => {
		if (error && panelEl) {
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
		error = '';
		loading = true;
		try {
			const res = await api.login({ email, password });
			token.login(res.token);
			goto('/');
		} catch (err: unknown) {
			error = (err as { message?: string }).message ?? 'Login failed';
		} finally {
			loading = false;
		}
	}
</script>

<div class="auth-centered">
	<div class="panel panel-accent w-full max-w-[400px]" bind:this={panelEl}>
		<p class="section-label mb-4">AUTH // SIGN IN</p>
		<h1 class="page-title-md mb-6">Sign in</h1>

		{#if error}
			<p class="error-box mb-4">{error}</p>
		{/if}

		<form onsubmit={submit} class="flex flex-col gap-4">
			<div>
				<label class="form-label" for="email">Email</label>
				<input id="email" type="email" bind:value={email} required class="h-10 w-full px-3 text-sm" />
			</div>
			<div>
				<label class="form-label" for="password">Password</label>
				<input id="password" type="password" bind:value={password} required class="h-10 w-full px-3 text-sm" />
			</div>
			<button
				type="submit"
				disabled={loading}
				class="btn btn-primary w-full py-[10px]"
				style:opacity={loading ? 0.5 : 1}
			>
				{loading ? 'Authenticating…' : 'Sign in'}
			</button>
		</form>

		<p class="mt-5 text-center text-sm text-text-secondary">
			No account? <a href="/register" class="text-primary font-semibold">Register</a>
		</p>
	</div>
</div>
