import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { TextPlugin } from 'gsap/TextPlugin';

gsap.registerPlugin(ScrollTrigger, TextPlugin);

// --- Route order map for directional page transitions ---

const ROUTE_ORDER: Record<string, number> = {
	'/': 0,
	'/login': 0.5,
	'/register': 0.5,
	'/coaches': 1,
	'/bookings': 2,
	'/profile': 3,
	'/coach/availability': 4,
	'/coach/services': 5,
	'/admin/coaches': 2,
	'/pending-approval': 2
};

export function getRouteIndex(pathname: string): number {
	if (ROUTE_ORDER[pathname] !== undefined) return ROUTE_ORDER[pathname];
	// Handle dynamic segments: /coaches/123 → 1.5, /bookings/456 → 2.5
	for (const [route, order] of Object.entries(ROUTE_ORDER)) {
		if (route !== '/' && pathname.startsWith(route + '/')) {
			return order + 0.5;
		}
	}
	return 0;
}

export function getDirection(fromPath: string, toPath: string): 'left' | 'right' {
	const fromIndex = getRouteIndex(fromPath);
	const toIndex = getRouteIndex(toPath);
	return toIndex >= fromIndex ? 'right' : 'left';
}

// --- Page transition animations ---

export function animatePageExit(
	container: HTMLElement,
	direction: 'left' | 'right'
): Promise<void> {
	return new Promise((resolve) => {
		gsap.to(container, {
			x: direction === 'right' ? -60 : 60,
			opacity: 0,
			duration: 0.25,
			ease: 'power2.in',
			onComplete: resolve
		});
	});
}

export function animatePageEnter(
	container: HTMLElement,
	direction: 'left' | 'right'
): gsap.core.Tween {
	return gsap.fromTo(
		container,
		{ x: direction === 'right' ? 60 : -60, opacity: 0 },
		{ x: 0, opacity: 1, duration: 0.3, ease: 'power2.out' }
	);
}

// --- Reusable animation presets ---

export function animateStaggerIn(
	elements: HTMLElement[] | NodeListOf<Element> | string,
	scope?: HTMLElement
): gsap.core.Tween | null {
	const targets = typeof elements === 'string' ? (scope ?? document).querySelectorAll(elements) : elements;
	if (!targets || (targets instanceof NodeList && targets.length === 0)) return null;
	if (Array.isArray(targets) && targets.length === 0) return null;

	return gsap.from(targets, {
		y: 20,
		opacity: 0,
		duration: 0.4,
		stagger: 0.06,
		ease: 'power2.out'
	});
}

export function animateShake(element: HTMLElement): gsap.core.Tween {
	return gsap.to(element, {
		keyframes: [
			{ x: -8, duration: 0.05 },
			{ x: 8, duration: 0.05 },
			{ x: -6, duration: 0.05 },
			{ x: 6, duration: 0.05 },
			{ x: -3, duration: 0.05 },
			{ x: 3, duration: 0.05 },
			{ x: 0, duration: 0.05 }
		],
		ease: 'power2.out'
	});
}

export function animatePulse(element: HTMLElement): gsap.core.Tween {
	return gsap.to(element, {
		opacity: 0.6,
		duration: 0.6,
		repeat: -1,
		yoyo: true,
		ease: 'sine.inOut'
	});
}

export function animateSlideUp(element: HTMLElement): gsap.core.Tween {
	return gsap.from(element, {
		y: 20,
		opacity: 0,
		duration: 0.3,
		ease: 'power2.out'
	});
}

export function animateGlowFlash(
	element: HTMLElement,
	glowValue: string
): gsap.core.Timeline {
	const tl = gsap.timeline();
	tl.to(element, { boxShadow: glowValue, duration: 0.15, ease: 'power2.out' });
	tl.to(element, { boxShadow: 'none', duration: 0.45, ease: 'power2.inOut' }, '+=0.1');
	return tl;
}

export function animateWizardStep(
	container: HTMLElement,
	forward: boolean
): gsap.core.Tween {
	return gsap.from(container, {
		x: forward ? 60 : -60,
		opacity: 0,
		duration: 0.3,
		ease: 'power2.out'
	});
}

export function animateSuccess(element: HTMLElement): gsap.core.Timeline {
	const tl = gsap.timeline();
	tl.from(element, { scale: 0.95, opacity: 0, duration: 0.3, ease: 'power2.out' });
	tl.to(element, {
		boxShadow: '0 0 12px rgba(26,122,60,0.8), 0 0 32px rgba(26,122,60,0.3)',
		duration: 0.2,
		ease: 'power2.out'
	});
	tl.to(element, {
		boxShadow: 'none',
		duration: 0.4,
		ease: 'power2.inOut'
	});
	return tl;
}

export function animateScaleIn(element: HTMLElement): gsap.core.Tween {
	return gsap.from(element, {
		scale: 0.97,
		opacity: 0,
		duration: 0.35,
		ease: 'power2.out'
	});
}

export function killTweens(
	...tweens: (gsap.core.Tween | gsap.core.Timeline | null | undefined)[]
) {
	for (const t of tweens) t?.kill();
}

export function animateOpenSlotModal(panel: HTMLElement): Promise<void> {
	return new Promise((resolve) => {
		gsap.set(panel, { y: '100%' });
		const tl = gsap.timeline({ onComplete: resolve });
		tl.to(panel, { y: '0%', duration: 0.45, ease: 'power3.out' });
	});
}

export function animateCloseSlotModal(panel: HTMLElement): Promise<void> {
	return new Promise((resolve) => {
		gsap.timeline({ onComplete: resolve })
			.to(panel, { y: '100%', duration: 0.3, ease: 'power2.in' });
	});
}
