<script lang="ts">
	import { api, type TimeWindowResponse } from '$lib/api';
	import { untrack } from 'svelte';

	interface Props {
		value: string;
		coachId: number;
		serviceId: number | null;
		min: string;
		timeWindows?: TimeWindowResponse[];
	}

	let { value = $bindable(''), coachId, serviceId, min, timeWindows = [] }: Props = $props();

	const today = new Date();
	let displayYear = $state(today.getFullYear());
	let displayMonth = $state(today.getMonth()); // 0-indexed

	type DayInfo = { available: number; total: number } | 'loading' | 'error';
	let dayData = $state<Map<string, DayInfo>>(new Map());

	const MONTH_NAMES = [
		'January', 'February', 'March', 'April', 'May', 'June',
		'July', 'August', 'September', 'October', 'November', 'December'
	];
	const DAY_LABELS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

	let minYear = $derived(new Date(min + 'T00:00:00').getFullYear());
	let minMonth = $derived(new Date(min + 'T00:00:00').getMonth());

	let maxYear = $derived.by(() => {
		const fallback = today.getFullYear() + 10;
		if (!timeWindows.length) return fallback;
		const twMax = Math.max(...timeWindows.map((w) => new Date(w.endDate + 'T00:00:00').getFullYear()));
		return Math.max(fallback, twMax);
	});

	let availableYears = $derived.by(() => {
		const years: number[] = [];
		for (let y = minYear; y <= maxYear; y++) years.push(y);
		return years;
	});

	function toISODate(year: number, month: number, day: number): string {
		return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
	}

	function daysInMonth(year: number, month: number): number {
		return new Date(year, month + 1, 0).getDate();
	}

	function firstDayOffset(year: number, month: number): number {
		// Monday-first: 0=Mon … 6=Sun
		const dow = new Date(year, month, 1).getDay();
		return (dow + 6) % 7;
	}

	let calendarGrid = $derived.by(() => {
		const total = daysInMonth(displayYear, displayMonth);
		const offset = firstDayOffset(displayYear, displayMonth);
		const cells: (string | null)[] = [];
		for (let i = 0; i < offset; i++) cells.push(null);
		for (let d = 1; d <= total; d++) cells.push(toISODate(displayYear, displayMonth, d));
		while (cells.length % 7 !== 0) cells.push(null);
		return cells;
	});

	let canGoPrev = $derived.by(() => {
		const minDate = new Date(min + 'T00:00:00');
		return (
			displayYear > minDate.getFullYear() ||
			(displayYear === minDate.getFullYear() && displayMonth > minDate.getMonth())
		);
	});

	function prevMonth() {
		if (displayMonth === 0) { displayYear--; displayMonth = 11; }
		else displayMonth--;
	}

	function nextMonth() {
		if (displayMonth === 11) { displayYear++; displayMonth = 0; }
		else displayMonth++;
	}

	// Sync display month when value changes externally (e.g., user types in DateInput)
	$effect(() => {
		const v = value;
		if (v && v.length === 10) {
			const d = new Date(v + 'T00:00:00');
			if (!isNaN(d.getTime())) {
				const y = d.getFullYear();
				const m = d.getMonth();
				untrack(() => {
					if (y !== displayYear || m !== displayMonth) {
						displayYear = y;
						displayMonth = m;
					}
				});
			}
		}
	});

	// Counter to discard stale async results when month/service changes mid-flight
	let activeFetch = 0;

	$effect(() => {
		const year = displayYear;
		const month = displayMonth;
		const sid = serviceId;
		const cid = coachId;
		const minDate = min;

		const myFetch = ++activeFetch;

		const total = daysInMonth(year, month);
		const newMap = new Map<string, DayInfo>();
		const datesToFetch: string[] = [];

		for (let d = 1; d <= total; d++) {
			const dateStr = toISODate(year, month, d);
			if (dateStr >= minDate) {
				newMap.set(dateStr, 'loading');
				datesToFetch.push(dateStr);
			}
		}

		dayData = newMap;

		const fetches = datesToFetch.map(
			async (dateStr): Promise<{ dateStr: string; result: DayInfo }> => {
				try {
					const slots =
						sid !== null
							? await api.getSlotsByService(sid, dateStr)
							: await api.getSlotsByDate(cid, dateStr);
					const available = slots.filter((s) => s.status === 'AVAILABLE').length;
					return { dateStr, result: { available, total: slots.length } };
				} catch {
					return { dateStr, result: 'error' };
				}
			}
		);

		Promise.all(fetches).then((results) => {
			if (activeFetch !== myFetch) return; // stale — navigated away
			const updated = new Map<string, DayInfo>();
			for (const { dateStr, result } of results) {
				updated.set(dateStr, result);
			}
			dayData = updated;
		});
	});

	function getDayState(
		dateStr: string
	): 'selected' | 'past' | 'loading' | 'uncovered' | 'full' | 'low' | 'mid' | 'high' {
		if (dateStr === value) return 'selected';
		if (dateStr < min) return 'past';
		const entry = dayData.get(dateStr);
		if (!entry || entry === 'loading') return 'loading';
		if (entry === 'error') return 'uncovered';
		if (entry.total === 0) return 'uncovered';
		if (entry.available === 0) return 'full';
		const ratio = entry.available / entry.total;
		if (ratio <= 0.4) return 'low';
		if (ratio <= 0.7) return 'mid';
		return 'high';
	}

	function selectDay(dateStr: string) {
		const state = getDayState(dateStr);
		if (state === 'past' || state === 'loading' || state === 'uncovered' || state === 'full') return;
		value = dateStr;
	}

	function getAvailableCount(dateStr: string): number | null {
		const entry = dayData.get(dateStr);
		if (!entry || entry === 'loading' || entry === 'error') return null;
		return entry.available;
	}
</script>

<div class="cal-root">
	<!-- Header -->
	<div class="cal-header">
		<button class="cal-nav" onclick={prevMonth} disabled={!canGoPrev} aria-label="Previous month">
			←
		</button>
		<div class="cal-selects">
			<select
				class="cal-select"
				bind:value={displayMonth}
				aria-label="Month"
			>
				{#each MONTH_NAMES as name, i}
					<option value={i} disabled={displayYear === minYear && i < minMonth}>{name}</option>
				{/each}
			</select>
			<select
				class="cal-select cal-select-year"
				bind:value={displayYear}
				aria-label="Year"
			>
				{#each availableYears as y}
					<option value={y}>{y}</option>
				{/each}
			</select>
		</div>
		<button class="cal-nav" onclick={nextMonth} aria-label="Next month">→</button>
	</div>

	<!-- Grid -->
	<div class="cal-grid">
		{#each DAY_LABELS as label}
			<div class="cal-dow">{label}</div>
		{/each}

		{#each calendarGrid as dateStr}
			{#if dateStr === null}
				<div class="cal-cell"></div>
			{:else}
				{@const state = getDayState(dateStr)}
				{@const dayNum = parseInt(dateStr.slice(8))}
				{@const count = getAvailableCount(dateStr)}
				{@const clickable = state !== 'past' && state !== 'loading' && state !== 'uncovered' && state !== 'full'}
				<button
					class="cal-cell cal-day cal-day-{state}"
					onclick={() => selectDay(dateStr)}
					disabled={!clickable}
					aria-label="{dateStr}{count !== null ? `, ${count} available` : ''}"
					aria-pressed={dateStr === value}
				>
					<span class="cal-num">{dayNum}</span>
					{#if count !== null && count > 0}
						<span class="cal-dot"></span>
					{/if}
				</button>
			{/if}
		{/each}
	</div>

	<!-- Legend -->
	<div class="cal-legend">
		<span class="leg"><span class="swatch sw-uncovered"></span>No slots</span>
		<span class="leg"><span class="swatch sw-full"></span>Full</span>
		<span class="leg"><span class="swatch sw-low"></span>Limited</span>
		<span class="leg"><span class="swatch sw-mid"></span>Available</span>
		<span class="leg"><span class="swatch sw-high"></span>Many slots</span>
	</div>
</div>

<style>
	.cal-root {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	/* ── Header ── */
	.cal-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 2px;
	}

	.cal-selects {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.cal-select {
		background: var(--color-surface-2);
		border: 1px solid var(--color-surface-border);
		color: var(--color-text-primary);
		font-family: var(--font-mono);
		font-size: 0.68rem;
		letter-spacing: 0.06em;
		height: 28px;
		padding: 0 6px;
		cursor: pointer;
		appearance: none;
		-webkit-appearance: none;
		transition: border-color 150ms ease, box-shadow 150ms ease;
	}
	.cal-select:hover {
		border-color: rgba(26, 62, 207, 0.5);
	}
	.cal-select:focus {
		outline: none;
		border-color: var(--color-primary);
		box-shadow: 0 0 6px rgba(26, 62, 207, 0.3);
	}
	.cal-select-year {
		width: 60px;
		text-align: center;
	}

	.cal-nav {
		background: none;
		border: 1px solid var(--color-surface-border);
		color: var(--color-text-secondary);
		font-family: var(--font-mono);
		font-size: 0.78rem;
		width: 28px;
		height: 28px;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		transition: border-color 150ms ease, color 150ms ease, box-shadow 150ms ease;
	}
	.cal-nav:hover:not(:disabled) {
		border-color: var(--color-primary);
		color: var(--color-primary);
		box-shadow: 0 0 6px rgba(26, 62, 207, 0.3);
	}
	.cal-nav:disabled {
		opacity: 0.25;
		cursor: default;
	}

	/* ── Grid ── */
	.cal-grid {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 2px;
	}

	.cal-dow {
		text-align: center;
		font-family: var(--font-mono);
		font-size: 0.55rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--color-text-muted);
		padding: 4px 0 6px;
	}

	/* ── Base cell ── */
	.cal-cell {
		aspect-ratio: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 2px;
		border: 1px solid transparent;
		background: transparent;
		cursor: default;
		transition: background 150ms ease, border-color 150ms ease, box-shadow 150ms ease;
	}

	.cal-day {
		background: var(--color-surface-2);
	}

	.cal-num {
		font-family: var(--font-mono);
		font-size: 0.68rem;
		font-weight: 500;
		line-height: 1;
	}

	.cal-dot {
		width: 3px;
		height: 3px;
		background: currentColor;
		opacity: 0.45;
		flex-shrink: 0;
	}

	/* ── Day states ── */

	/* Past */
	.cal-day-past {
		opacity: 0.2;
		cursor: not-allowed;
	}
	.cal-day-past .cal-num {
		color: var(--color-text-muted);
	}

	/* Loading (pulsing skeleton) */
	.cal-day-loading {
		animation: cal-pulse 1.4s ease-in-out infinite;
	}
	.cal-day-loading .cal-num {
		color: var(--color-text-muted);
	}
	@keyframes cal-pulse {
		0%, 100% { opacity: 0.55; }
		50%       { opacity: 0.2; }
	}

	/* Uncovered — no time windows / no slots */
	.cal-day-uncovered {
		background: var(--color-surface-1);
		border-color: transparent;
		cursor: not-allowed;
	}
	.cal-day-uncovered .cal-num {
		color: var(--color-text-muted);
		opacity: 0.4;
	}

	/* Fully booked */
	.cal-day-full {
		background: rgba(192, 21, 44, 0.1);
		border-color: rgba(192, 21, 44, 0.18);
		cursor: not-allowed;
	}
	.cal-day-full .cal-num {
		color: #f87171;
	}

	/* Low availability (≤ 40%) */
	.cal-day-low {
		background: rgba(240, 168, 64, 0.09);
		border-color: rgba(240, 168, 64, 0.15);
		cursor: pointer;
	}
	.cal-day-low .cal-num {
		color: #f0a840;
	}
	.cal-day-low:hover {
		background: rgba(240, 168, 64, 0.18);
		border-color: rgba(240, 168, 64, 0.38);
	}

	/* Mid availability (41–70%) */
	.cal-day-mid {
		background: rgba(26, 62, 207, 0.1);
		border-color: rgba(26, 62, 207, 0.18);
		cursor: pointer;
	}
	.cal-day-mid .cal-num {
		color: var(--color-text-secondary);
	}
	.cal-day-mid:hover {
		background: rgba(26, 62, 207, 0.2);
		border-color: rgba(26, 62, 207, 0.4);
	}

	/* High availability (> 70%) */
	.cal-day-high {
		background: rgba(26, 62, 207, 0.2);
		border-color: rgba(26, 62, 207, 0.32);
		cursor: pointer;
	}
	.cal-day-high .cal-num {
		color: var(--color-text-primary);
	}
	.cal-day-high:hover {
		background: rgba(26, 62, 207, 0.3);
		border-color: var(--color-primary);
		box-shadow: 0 0 8px rgba(26, 62, 207, 0.35);
	}

	/* Selected */
	.cal-day-selected {
		background: rgba(26, 62, 207, 0.22) !important;
		border-color: var(--color-primary) !important;
		box-shadow: var(--glow-primary) !important;
		cursor: pointer;
	}
	.cal-day-selected .cal-num {
		color: var(--color-primary) !important;
		font-weight: 700;
	}

	/* ── Legend ── */
	.cal-legend {
		display: flex;
		flex-wrap: wrap;
		gap: 8px 12px;
		padding-top: 8px;
		border-top: 1px solid rgba(26, 62, 207, 0.1);
		margin-top: 2px;
	}

	.leg {
		display: flex;
		align-items: center;
		gap: 5px;
		font-family: var(--font-mono);
		font-size: 0.55rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--color-text-muted);
	}

	.swatch {
		width: 8px;
		height: 8px;
		flex-shrink: 0;
	}

	.sw-uncovered {
		background: var(--color-surface-1);
		border: 1px solid rgba(26, 62, 207, 0.2);
	}
	.sw-full  { background: rgba(192, 21, 44, 0.45); }
	.sw-low   { background: rgba(240, 168, 64, 0.55); }
	.sw-mid   { background: rgba(26, 62, 207, 0.35); }
	.sw-high  { background: rgba(26, 62, 207, 0.65); }
</style>
