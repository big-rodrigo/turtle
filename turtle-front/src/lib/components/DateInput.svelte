<script lang="ts">
  import { untrack } from 'svelte';

  interface Props {
    id?: string;
    value?: string;          // YYYY-MM-DD; bindable
    min?: string;            // YYYY-MM-DD optional minimum date
    required?: boolean;
    class?: string;
  }

  let { id, value = $bindable(''), min, required = false, class: extraClass = '' }: Props = $props();

  // ── helpers ─────────────────────────────────────────────────────────────
  function toDisplay(iso: string): string {
    if (!iso || iso.length < 10) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  function toISO(display: string): string | null {
    const parts = display.split('/');
    if (parts.length !== 3) return null;
    const [d, m, y] = parts;
    if (d.length !== 2 || m.length !== 2 || y.length !== 4) return null;
    const iso = `${y}-${m}-${d}`;
    const date = new Date(iso);
    if (isNaN(date.getTime())) return null;
    // re-format to catch invalid days like 31/02
    const check = date.toISOString().slice(0, 10);
    return check === iso ? iso : null;
  }

  // ── display state ────────────────────────────────────────────────────────
  let display = $state(toDisplay(value));

  $effect(() => {
    // sync inbound changes (e.g. form reset) — only tracks `value`, not `display`
    const expected = toDisplay(value);
    untrack(() => {
      if (display !== expected) display = expected;
    });
  });

  // ── mask logic ───────────────────────────────────────────────────────────
  function handleInput(e: Event) {
    const input = e.target as HTMLInputElement;
    const raw = input.value.replace(/\D/g, '').slice(0, 8);

    let masked = '';
    for (let i = 0; i < raw.length; i++) {
      if (i === 2 || i === 4) masked += '/';
      masked += raw[i];
    }

    display = masked;
    input.value = masked;

    // partial input → clear bound value
    if (masked.length < 10) {
      if (value !== '') value = '';
    }
  }

  function handleBlur(e: Event) {
    const input = e.target as HTMLInputElement;
    const iso = toISO(display);

    if (!iso) {
      display = toDisplay(value); // revert to last valid or empty
      input.setCustomValidity(display === '' && required ? 'Please enter a date.' : '');
      return;
    }

    if (min && iso < min) {
      input.setCustomValidity(`Date must be on or after ${toDisplay(min)}.`);
    } else {
      input.setCustomValidity('');
    }

    value = iso;
    display = toDisplay(iso);
  }
</script>

<input
  {id}
  type="text"
  inputmode="numeric"
  placeholder="dd/mm/yyyy"
  maxlength="10"
  {required}
  value={display}
  oninput={handleInput}
  onblur={handleBlur}
  class="h-[40px] w-full px-3 py-2 text-sm {extraClass}"
/>
