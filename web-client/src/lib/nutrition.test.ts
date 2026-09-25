import { describe, expect, it } from 'vitest';

import { belowBmr, bmr, dailyCalorieTarget, impliedKcal, macroGrams, proteinCapped, tdeeMaintain } from './nutrition';

// Expected values are what backend-core's NutritionCalculator returns for the
// same inputs: the live preview in Profile/Objective must never disagree with
// the targets the server actually saves.
describe('nutrition maths (mirrors backend NutritionCalculator)', () => {
  it('computes Mifflin-St Jeor BMR and TDEE', () => {
    expect(bmr('MALE', 30, 180, 80)).toBe(1780);
    expect(tdeeMaintain('MALE', 30, 180, 80, 'MODERATE')).toBe(2759);
    expect(bmr('FEMALE', 30, 165, 60)).toBe(1320);
  });

  it('returns null while any input is missing', () => {
    expect(bmr(null, 30, 180, 80)).toBeNull();
    expect(tdeeMaintain('MALE', 30, 180, 80, null)).toBeNull();
  });

  it('applies the goal percentage, clamped to 0-50', () => {
    expect(dailyCalorieTarget(2759, 'LOSE', 20)).toBe(2207);
    expect(dailyCalorieTarget(2759, 'MAINTAIN', 20)).toBe(2759);
    expect(dailyCalorieTarget(2000, 'GAIN', 90)).toBe(3000);
  });

  it('splits calories into macro grams per preset', () => {
    expect(macroGrams(2207, 'MAINTAIN_MUSCLE')).toEqual({ protein: 193, carbs: 221, fat: 61 });
    expect(macroGrams(2759, null)).toEqual({ protein: 207, carbs: 345, fat: 61 });
  });

  it('caps protein at 2.2 g/kg and moves the rest to carbs and fat (same numbers as the backend)', () => {
    expect(macroGrams(2759, 'BALANCED', 80)).toEqual({ protein: 176, carbs: 367, fat: 65 });
    expect(macroGrams(2207, 'MAINTAIN_MUSCLE', 80)).toEqual({ protein: 176, carbs: 231, fat: 64 });
    expect(macroGrams(3000, 'MAINTAIN_MUSCLE', 60)).toEqual({ protein: 132, carbs: 380, fat: 106 });
    expect(macroGrams(3000, 'KETOGENIC', 60)).toEqual({ protein: 132, carbs: 41, fat: 256 });
    expect(macroGrams(2000, 'BALANCED', 80)).toEqual(macroGrams(2000, 'BALANCED'));
    expect(proteinCapped(2759, 'BALANCED', 80)).toBe(true);
    expect(proteinCapped(2000, 'BALANCED', 80)).toBe(false);
  });

  it('never lets a losing target drop below BMR', () => {
    // Sedentary: BMR 1805, TDEE 2166. Cutting 30% would be 1516.
    expect(dailyCalorieTarget(2166, 'LOSE', 30, 1805)).toBe(1805);
    expect(dailyCalorieTarget(2166, 'LOSE', 10, 1805)).toBe(1949);
    expect(belowBmr(2166, 1805, 15)).toBe(false);
    expect(belowBmr(2166, 1805, 20)).toBe(true);
  });

  it('derives kcal from macros with 4/4/9', () => {
    expect(impliedKcal(10, 20, 5)).toBe(165);
  });
});
