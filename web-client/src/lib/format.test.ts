import { describe, expect, it } from 'vitest';

import {
  addDays,
  addMonths,
  daysBetween,
  endOfMonth,
  fmt,
  fmt0,
  fromIso,
  parseGrams,
  parseNumberOr,
  prettyMeal,
  startOfMonth,
  startOfWeek,
  toIso,
} from './format';

describe('number formatting', () => {
  it('drops a trailing .0 and otherwise shows one decimal', () => {
    expect(fmt(12)).toBe('12');
    expect(fmt(12.345)).toBe('12.3');
    expect(fmt(null)).toBe('-');
    expect(fmt0(12.6)).toBe('13');
  });

  it('parses user-typed numbers with a fallback', () => {
    expect(parseNumberOr('12,5', 1)).toBe(12.5);
    expect(parseNumberOr('', 7)).toBe(7);
    expect(parseNumberOr('abc', 7)).toBe(7);
    expect(parseNumberOr('-3', 7)).toBe(7);
  });

  it('reads gram weights out of AI quantities', () => {
    expect(parseGrams('200 g')).toBe(200);
    expect(parseGrams('1.5 kg')).toBe(1500);
    expect(parseGrams('250ml')).toBe(250);
    expect(parseGrams('2')).toBe(0);
    expect(parseGrams(null)).toBe(0);
  });

  it('prettifies meal names', () => {
    expect(prettyMeal('BREAKFAST')).toBe('Breakfast');
  });
});

describe('local-date helpers (never UTC-shifted)', () => {
  it('round-trips ISO dates', () => {
    expect(toIso(fromIso('2026-03-29'))).toBe('2026-03-29');
  });

  it('moves across month and year boundaries', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addMonths('2026-01-15', 1)).toBe('2026-02-15');
  });

  it('finds week and month boundaries', () => {
    expect(startOfWeek('2026-09-24')).toBe('2026-09-21'); // Thursday -> Monday
    expect(startOfWeek('2026-09-21')).toBe('2026-09-21');
    expect(startOfWeek('2026-09-27')).toBe('2026-09-21'); // Sunday belongs to the same week
    expect(startOfMonth('2026-02-17')).toBe('2026-02-01');
    expect(endOfMonth('2024-02-10')).toBe('2024-02-29');
    expect(endOfMonth('2026-02-10')).toBe('2026-02-28');
  });

  it('counts days inclusively', () => {
    expect(daysBetween('2026-09-21', '2026-09-27')).toBe(7);
    expect(daysBetween('2026-09-24', '2026-09-24')).toBe(1);
  });
});
