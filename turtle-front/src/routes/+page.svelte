<script lang="ts">
	import { onMount } from 'svelte';
	import { role } from '$lib/auth';
	import { goto } from '$app/navigation';
	import ScrollHero from '$lib/components/ScrollHero.svelte';
	import gsap from 'gsap';
	import { ScrollTrigger } from 'gsap/ScrollTrigger';

	$effect(() => {
		if ($role === 'CLIENT' || $role === 'COACH') goto('/bookings');
		else if ($role === 'COACH_PENDING') goto('/pending-approval');
		else if ($role === 'ADMIN') goto('/admin/coaches');
	});

	let howItWorksSection: HTMLElement;

	onMount(() => {
		// "How It Works" panels slide in from left
		const featurePanels = howItWorksSection.querySelectorAll('.feature-panel');
		gsap.from(featurePanels, {
			x: -60,
			opacity: 0,
			duration: 0.6,
			stagger: 0.15,
			ease: 'power2.out',
			scrollTrigger: {
				trigger: howItWorksSection,
				start: 'top 80%',
				toggleActions: 'play none none none'
			}
		});

		return () => {
			ScrollTrigger.getAll().forEach((st) => st.kill());
		};
	});
</script>

<div class="select-none">
	<ScrollHero />

	<!-- How It Works -->
	<section bind:this={howItWorksSection} class="landing-section">
		<p class="section-label">PROTOCOL // HOW IT WORKS</p>
		<h2 class="page-title-md mb-8">Three steps to growth</h2>

		<div class="flex flex-col gap-4">
			<div class="panel panel-accent flex items-start gap-4 feature-panel">
				<span class="step-number">01</span>
				<div>
					<h3 class="panel-title mb-1">Find Your Coach</h3>
					<p class="text-sm text-text-secondary">
						Browse experienced coaches by specialty. Read profiles, check availability, choose the
						right fit.
					</p>
				</div>
			</div>

			<div class="panel panel-accent flex items-start gap-4 feature-panel">
				<span class="step-number">02</span>
				<div>
					<h3 class="panel-title mb-1">Book a Session</h3>
					<p class="text-sm text-text-secondary">
						Pick a time that works, pay securely, and you're in. No back-and-forth emails.
					</p>
				</div>
			</div>

			<div class="panel panel-accent flex items-start gap-4 feature-panel">
				<span class="step-number">03</span>
				<div>
					<h3 class="panel-title mb-1">Grow Together</h3>
					<p class="text-sm text-text-secondary">
						Get personalized guidance, share resources, and track your progress through direct chat
						with your coach.
					</p>
				</div>
			</div>
		</div>
	</section>

	<!-- Final CTA -->
	<section class="landing-section text-center">
		<p class="section-label mb-2">INITIATE // CONNECTION</p>
		<h2 class="page-title-md mb-3">Ready to level up?</h2>
		<p class="text-text-secondary mb-6 max-w-[480px] mx-auto">
			Your next breakthrough is one session away. Find a coach who gets it.
		</p>
		<a href="/coaches" class="btn btn-primary btn-lg shadow-glow-primary">Browse Coaches</a>
	</section>
</div>
