import { browser } from '$app/environment';
import { writable, derived } from 'svelte/store';
import { api, type JwtPayload, type UserRole } from './api';

const TOKEN_KEY = 'turtle_token';

function parseJwt(token: string): JwtPayload | null {
	try {
		const payload = token.split('.')[1];
		return JSON.parse(atob(payload)) as JwtPayload;
	} catch {
		return null;
	}
}

function loadToken(): string | null {
	if (!browser) return null;
	return localStorage.getItem(TOKEN_KEY);
}

function createAuthStore() {
	const stored = loadToken();
	const { subscribe, set } = writable<string | null>(stored);

	if (stored) api.setToken(stored);

	return {
		subscribe,
		login(token: string) {
			localStorage.setItem(TOKEN_KEY, token);
			api.setToken(token);
			set(token);
		},
		logout() {
			localStorage.removeItem(TOKEN_KEY);
			api.setToken(null);
			set(null);
		}
	};
}

export const token = createAuthStore();

export const user = derived(token, ($token) => {
	if (!$token) return null;
	return parseJwt($token);
});

export const role = derived(user, ($user): UserRole | null => {
	if (!$user) return null;
	const groups = $user.groups;
	return Array.isArray(groups) ? groups[0] : groups;
});

export const userId = derived(user, ($user): number | null =>
	$user ? parseInt($user.sub) : null
);
