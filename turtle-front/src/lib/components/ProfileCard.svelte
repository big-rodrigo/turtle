<script lang="ts">
	import type { SocialLink, SocialLinkType } from '$lib/api';

	interface Props {
		name: string;
		description?: string;
		specialty?: string;
		pictureUrl?: string;
		socialLinks?: SocialLink[];
		compact?: boolean;
	}

	let { name, description, specialty, pictureUrl, socialLinks = [], compact = false }: Props = $props();

	const LINK_LABELS: Record<SocialLinkType, string> = {
		INSTAGRAM: 'IG',
		TWITTER: 'TW',
		LINKEDIN: 'LI',
		YOUTUBE: 'YT',
		TIKTOK: 'TK',
		FACEBOOK: 'FB',
		CUSTOM: 'LINK'
	};

	function initials(n: string): string {
		return n
			.split(' ')
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('');
	}

	let avatarSize = $derived(compact ? 'w-10 h-10' : 'w-16 h-16');
</script>

<div class="panel flex flex-col gap-3">
	<div class="flex items-center gap-4">
		<!-- Avatar -->
		<div class="shrink-0 overflow-hidden bg-surface-2 border border-surface-border flex items-center justify-center {avatarSize}">
			{#if pictureUrl}
				<img src={pictureUrl} alt="{name} profile" class="avatar-img" />
			{:else}
				<span class="mono font-bold text-primary {compact ? 'text-sm' : 'text-lg'}">{initials(name)}</span>
			{/if}
		</div>

		<!-- Name + specialty -->
		<div class="flex flex-col gap-0.5 min-w-0">
			<span class="font-semibold text-text-primary {compact ? 'text-sm' : 'text-base'} truncate">{name}</span>
			{#if specialty}
				<span class="mono text-[0.65rem] uppercase tracking-widest text-primary">{specialty}</span>
			{/if}
		</div>
	</div>

	{#if !compact}
		{#if description}
			<p class="text-sm text-text-secondary leading-relaxed">{description}</p>
		{/if}

		{#if socialLinks.length > 0}
			<div class="flex flex-wrap gap-2 pt-1">
				{#each socialLinks as link}
					<a
						href={link.url}
						target="_blank"
						rel="noopener noreferrer"
						class="social-link mono text-[0.65rem] uppercase tracking-wider px-2 py-1 border border-surface-border text-text-secondary"
					>
						{link.label || LINK_LABELS[link.type]}
					</a>
				{/each}
			</div>
		{/if}
	{/if}
</div>

<style>
	.social-link {
		text-decoration: none;
		transition: color 150ms ease, border-color 150ms ease, text-shadow 150ms ease;
	}
	.social-link:hover {
		color: var(--color-primary-light);
		border-color: rgba(26, 62, 207, 0.6);
		text-shadow: 0 0 8px rgba(26, 62, 207, 0.6);
	}
	.avatar-img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}
</style>
