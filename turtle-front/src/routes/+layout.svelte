<script lang="ts">
	import favicon from '$lib/assets/favicon.svg';
	import '../app.css';
	import Background from '$lib/components/Background.svelte';
	import PageTransition from '$lib/components/PageTransition.svelte';
	import { role, token } from '$lib/auth';
	import { goto } from '$app/navigation';

	let { children } = $props();

	function logout() {
		token.logout();
		goto('/');
	}
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<Background />

<header class="nav-header">
	<nav class="nav-inner">
		<a href={$role === 'CLIENT' || $role === 'COACH' ? '/bookings' : $role === 'ADMIN' ? '/admin/coaches' : $role === 'COACH_PENDING' ? '/pending-approval' : '/'} class="brand-link">TURTLE</a>

		<div class="flex items-center gap-6">
			<a href="/coaches" class="nav-link">Coaches</a>
			{#if $role === 'CLIENT' || $role === 'COACH'}
				<a href="/bookings" class="nav-link">Bookings</a>
				<a href="/profile" class="nav-link">Profile</a>
			{/if}
			{#if $role === 'COACH'}
				<a href="/coach/availability" class="nav-link">Availability</a>
				<a href="/coach/services" class="nav-link">Services</a>
			{/if}
			{#if $role === 'ADMIN'}
				<a href="/admin/coaches" class="nav-link">Admin</a>
			{/if}
			{#if $role}
				<button onclick={logout} class="nav-link nav-logout">Sign out</button>
			{:else}
				<a href="/login" class="nav-cta">Sign in</a>
			{/if}
		</div>
	</nav>
</header>

<main class="page-main">
	<PageTransition>
		{@render children()}
	</PageTransition>
</main>

<style>
	.nav-link {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.1em;
		color: var(--color-text-secondary);
		text-decoration: none;
		transition: color 150ms ease, text-shadow 150ms ease;
	}
	.nav-link:hover {
		color: var(--color-primary-light);
		text-shadow: 0 0 10px rgba(26, 62, 207, 0.7);
	}
</style>
