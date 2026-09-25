/**
 * Client-side mirror of backend NutritionCalculator / desktop NutritionCalc:
 * BMR, TDEE and macro grams. Used by the Profile and Objective views to preview
 * values live as the user types, without round-tripping to the server.
 */

import type { ActivityLevel, Goal, MacroPreset, Recipe, Sex } from '../api/types';

export const MACRO_PRESET_SPLIT: Record<
  MacroPreset,
  { protein: number; carbs: number; fat: number }
> = {
  BALANCED: { protein: 30, carbs: 50, fat: 20 },
  MAINTAIN_MUSCLE: { protein: 35, carbs: 40, fat: 25 },
  KETOGENIC: { protein: 25, carbs: 5, fat: 70 },
};

export const MACRO_PRESET_LABEL: Record<MacroPreset, string> = {
  BALANCED: 'Balanced (30 / 50 / 20)',
  MAINTAIN_MUSCLE: 'Maintain muscle mass (35 / 40 / 25)',
  KETOGENIC: 'Ketogenic (25 / 5 / 70)',
};

export const ACTIVITY_MULTIPLIER: Record<ActivityLevel, number> = {
  SEDENTARY: 1.2,
  LIGHT: 1.375,
  MODERATE: 1.55,
  ACTIVE: 1.725,
  VERY_ACTIVE: 1.9,
};

export const ACTIVITY_LABEL: Record<ActivityLevel, string> = {
  SEDENTARY: 'Sedentary (little or no exercise)',
  LIGHT: 'Light (1-3 light workouts/week)',
  MODERATE: 'Moderate (3-5 workouts/week)',
  ACTIVE: 'Active (6-7 workouts/week)',
  VERY_ACTIVE: 'Very high (twice a day / physical job)',
};

export const GOAL_LABEL: Record<Goal, string> = {
  LOSE: 'Lose weight',
  MAINTAIN: 'Maintain',
  GAIN: 'Gain weight',
};

export const SEX_LABEL: Record<Sex, string> = { MALE: 'Male', FEMALE: 'Female' };

/** Mifflin-St Jeor. Returns null when any input is missing. */
export function bmr(
  sex: Sex | null,
  age: number | null,
  heightCm: number | null,
  weightKg: number | null,
): number | null {
  if (sex == null || age == null || heightCm == null || weightKg == null) return null;
  const base = 10 * weightKg + 6.25 * heightCm - 5 * age;
  return Math.round(base + (sex === 'MALE' ? 5 : -161));
}

export function tdeeMaintain(
  sex: Sex | null,
  age: number | null,
  heightCm: number | null,
  weightKg: number | null,
  activity: ActivityLevel | null,
): number | null {
  const b = bmr(sex, age, heightCm, weightKg);
  if (b == null || activity == null) return null;
  return Math.round(b * ACTIVITY_MULTIPLIER[activity]);
}

/** TDEE adjusted by the goal percentage (clamped to 0-50), before the BMR floor. */
export function adjustedCalories(tdee: number, goal: Goal, goalPercent: number | null): number {
  const pct = Math.max(0, Math.min(50, goalPercent ?? 0));
  const adjust = goal === 'LOSE' ? -pct / 100 : goal === 'GAIN' ? pct / 100 : 0;
  return Math.round(tdee * (1 + adjust));
}

/**
 * The daily calorie target, never below BMR when it's known: eating less than
 * the body burns at rest isn't a safe diet (the backend enforces the same floor).
 */
export function dailyCalorieTarget(
  tdee: number | null,
  goal: Goal | null,
  goalPercent: number | null,
  bmrKcal: number | null = null,
): number | null {
  if (tdee == null || goal == null) return null;
  const target = adjustedCalories(tdee, goal, goalPercent);
  return bmrKcal == null ? target : Math.max(target, bmrKcal);
}

/** Whether cutting {@code percent} of TDEE would drop below BMR. */
export function belowBmr(tdee: number, bmrKcal: number, percent: number): boolean {
  return adjustedCalories(tdee, 'LOSE', percent) < bmrKcal;
}

/** More protein than this per kg of body weight has no extra benefit. */
export const MAX_PROTEIN_G_PER_KG = 2.2;

export interface MacroGrams {
  protein: number;
  carbs: number;
  fat: number;
}

/**
 * Macro grams for a calorie target. Protein is capped at 2.2 g per kg of body
 * weight (no cap when the weight is unknown); the calories above the cap go to
 * carbs and fat in the preset's own proportion, so the total stays the same.
 */
export function macroGrams(
  calories: number,
  preset: MacroPreset | null,
  weightKg: number | null = null,
): MacroGrams {
  const split = MACRO_PRESET_SPLIT[preset ?? 'BALANCED'];
  let proteinKcal = (calories * split.protein) / 100;
  let carbsKcal = (calories * split.carbs) / 100;
  let fatKcal = (calories * split.fat) / 100;
  if (weightKg != null && weightKg > 0) {
    const maxProteinKcal = MAX_PROTEIN_G_PER_KG * weightKg * 4;
    if (proteinKcal > maxProteinKcal) {
      const spare = proteinKcal - maxProteinKcal;
      const rest = split.carbs + split.fat;
      proteinKcal = maxProteinKcal;
      carbsKcal += (spare * split.carbs) / rest;
      fatKcal += (spare * split.fat) / rest;
    }
  }
  return {
    protein: Math.round(proteinKcal / 4),
    carbs: Math.round(carbsKcal / 4),
    fat: Math.round(fatKcal / 9),
  };
}

/** True when the preset would ask for more protein than 2.2 g/kg. */
export function proteinCapped(calories: number, preset: MacroPreset | null, weightKg: number | null): boolean {
  if (weightKg == null || weightKg <= 0) return false;
  const split = MACRO_PRESET_SPLIT[preset ?? 'BALANCED'];
  return (calories * split.protein) / 100 > MAX_PROTEIN_G_PER_KG * weightKg * 4;
}

// ---------------- recipe per-100 g maths ----------------

/** Cooked weight for per-100 g maths, falling back to the raw ingredient sum. */
export function effectiveCookedGrams(recipe: Recipe): number {
  if (recipe.totalCookedGrams > 0) return recipe.totalCookedGrams;
  return (recipe.ingredients ?? []).reduce((sum, line) => sum + line.amountGrams, 0);
}

function per100(total: number, recipe: Recipe): number {
  const grams = effectiveCookedGrams(recipe);
  return grams <= 0 ? 0 : (total * 100) / grams;
}

export const recipePer100g = {
  kcal: (r: Recipe) => per100(r.totalKcal, r),
  protein: (r: Recipe) => per100(r.totalProtein, r),
  carbs: (r: Recipe) => per100(r.totalCarbs, r),
  fat: (r: Recipe) => per100(r.totalFat, r),
  fiber: (r: Recipe) => per100(r.totalFiber, r),
};

// ---------------- shared constants ----------------

export const KCAL_PER_G = { protein: 4, carbs: 4, fat: 9 } as const;
/** How far entered kcal may differ from the macro-implied kcal (matches the desktop dialog). */
export const KCAL_TOLERANCE = 10;

/** kcal implied by the macros — the `4P + 4C + 9F` consistency check. */
export function impliedKcal(protein: number, carbs: number, fat: number): number {
  return KCAL_PER_G.protein * protein + KCAL_PER_G.carbs * carbs + KCAL_PER_G.fat * fat;
}
