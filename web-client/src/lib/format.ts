/** Formatting + date helpers shared across views. */

import type { IsoDate, Meal } from '../api/types';

/** Drop a trailing `.0`; otherwise one decimal. Mirrors AiView.fmt in the desktop client. */
export function fmt(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '-';
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

export function fmt0(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '-';
  return String(Math.round(value));
}

/** `BREAKFAST` -> `Breakfast`. */
export function prettyMeal(meal: Meal): string {
  return meal.charAt(0) + meal.slice(1).toLowerCase();
}

/** Today in the *browser's* timezone as `yyyy-MM-dd` (never UTC-shifted). */
export function today(): IsoDate {
  return toIso(new Date());
}

export function toIso(date: Date): IsoDate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Parse `yyyy-MM-dd` as a *local* date — `new Date(iso)` would parse it as UTC. */
export function fromIso(iso: IsoDate): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function addDays(iso: IsoDate, days: number): IsoDate {
  const date = fromIso(iso);
  date.setDate(date.getDate() + days);
  return toIso(date);
}

export function addMonths(iso: IsoDate, months: number): IsoDate {
  const date = fromIso(iso);
  date.setMonth(date.getMonth() + months);
  return toIso(date);
}

/** Monday of the ISO week containing `iso`. */
export function startOfWeek(iso: IsoDate): IsoDate {
  const date = fromIso(iso);
  const dow = (date.getDay() + 6) % 7; // Monday = 0
  date.setDate(date.getDate() - dow);
  return toIso(date);
}

export function startOfMonth(iso: IsoDate): IsoDate {
  const date = fromIso(iso);
  date.setDate(1);
  return toIso(date);
}

export function endOfMonth(iso: IsoDate): IsoDate {
  const date = fromIso(iso);
  date.setMonth(date.getMonth() + 1, 0);
  return toIso(date);
}

export function isAfter(a: IsoDate, b: IsoDate): boolean {
  return a > b; // ISO dates sort lexicographically
}

export function daysBetween(from: IsoDate, to: IsoDate): number {
  const ms = fromIso(to).getTime() - fromIso(from).getTime();
  return Math.round(ms / 86_400_000) + 1;
}

const PRETTY = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});
const DAY_MONTH = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });

/** "Mon, 12 Jun" — used in chart tooltips. */
export function prettyDate(iso: IsoDate): string {
  return PRETTY.format(fromIso(iso));
}

/** Axis tick: day number, or "d MMM" when the month changes. */
export function axisLabel(iso: IsoDate, previous: IsoDate | undefined): string {
  const date = fromIso(iso);
  const prevMonth = previous ? fromIso(previous).getMonth() : -1;
  return date.getMonth() === prevMonth ? String(date.getDate()) : DAY_MONTH.format(date);
}

/**
 * Extract a gram weight from the AI's free-text quantity field. Handles
 * "500g", "1.5 kg", "1500ml". Returns 0 when the quantity isn't a mass
 * (e.g. "1 cup"), which callers read as "leave the values as reported".
 */
export function parseGrams(quantity: string | null | undefined): number {
  if (!quantity) return 0;
  const match = /(\d+(?:\.\d+)?)\s*(kg|g|gram|grams|ml|l)\b/.exec(
    quantity.trim().toLowerCase().replace(',', '.'),
  );
  if (!match) return 0;
  const n = Number(match[1]);
  return match[2] === 'kg' || match[2] === 'l' ? n * 1000 : n;
}

export function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

/** Parse a user-typed number, falling back when the input is blank or invalid. */
export function parseNumberOr(raw: string | null | undefined, fallback: number): number {
  if (raw == null || raw.trim() === '') return fallback;
  const value = Number(raw.trim().replace(',', '.'));
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

/** Meal that best matches the current hour — mirrors AiView.defaultMealForNow. */
export function defaultMealForNow(): Meal {
  const hour = new Date().getHours();
  if (hour < 10) return 'BREAKFAST';
  if (hour < 14) return 'LUNCH';
  if (hour < 18) return 'SNACK';
  return 'DINNER';
}
