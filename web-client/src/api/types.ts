/**
 * TypeScript mirror of the backend-core / ai-service DTOs.
 *
 * Field names match the Java records exactly (Jackson serialises record
 * components verbatim), so these are the wire shapes — do not rename.
 */

export type Meal = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';
export const MEALS: Meal[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'];

export type Sex = 'MALE' | 'FEMALE';
export const SEXES: Sex[] = ['MALE', 'FEMALE'];

export type ActivityLevel = 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'ACTIVE' | 'VERY_ACTIVE';
export const ACTIVITY_LEVELS: ActivityLevel[] = [
  'SEDENTARY',
  'LIGHT',
  'MODERATE',
  'ACTIVE',
  'VERY_ACTIVE',
];

export type Goal = 'LOSE' | 'MAINTAIN' | 'GAIN';
export const GOALS: Goal[] = ['LOSE', 'MAINTAIN', 'GAIN'];

export type MacroPreset = 'BALANCED' | 'MAINTAIN_MUSCLE' | 'KETOGENIC';
export const MACRO_PRESETS: MacroPreset[] = ['BALANCED', 'MAINTAIN_MUSCLE', 'KETOGENIC'];

/** ISO-8601 calendar date, `yyyy-MM-dd`. */
export type IsoDate = string;

// ---------------- profile / objective ----------------

export interface Profile {
  id: number | null;
  userKey: string | null;
  displayName: string | null;
  sex: Sex | null;
  age: number | null;
  heightCm: number | null;
  weightKg: number | null;
  activityLevel: ActivityLevel | null;
  bmr: number | null;
  tdeeMaintain: number | null;
}

export interface UpdateProfileRequest {
  displayName?: string | null;
  sex?: Sex | null;
  age?: number | null;
  heightCm?: number | null;
  weightKg?: number | null;
  activityLevel?: ActivityLevel | null;
}

export interface Objective {
  goal: Goal | null;
  goalPercent: number | null;
  macroPreset: MacroPreset | null;
  dailyCalorieTarget: number | null;
  dailyProteinTargetG: number | null;
  dailyCarbsTargetG: number | null;
  dailyFatTargetG: number | null;
  dailyFiberTargetG: number | null;
  dailyWaterTargetMl: number | null;
}

export interface UpdateObjectiveRequest {
  goal: Goal;
  goalPercent: number;
  macroPreset: MacroPreset;
  dailyFiberTargetG: number;
  dailyWaterTargetMl: number;
}

// ---------------- library ----------------

export interface Ingredient {
  id: number;
  name: string;
  brand: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number;
  isPublic: boolean;
}

export interface CreateIngredientRequest {
  name: string;
  brand: string | null;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number;
}

export interface RecipeLine {
  ingredientId: number;
  ingredientName: string;
  amountGrams: number;
}

export interface Recipe {
  id: number;
  name: string;
  servings: number;
  totalKcal: number;
  totalProtein: number;
  totalCarbs: number;
  totalFat: number;
  totalFiber: number;
  totalCookedGrams: number;
  isPublic: boolean;
  ingredients: RecipeLine[];
}

export interface CreateRecipeRequest {
  name: string;
  servings: number;
  ingredients: { ingredientId: number; amountGrams: number }[];
  totalCookedGrams: number;
}

// ---------------- diary / water / weight ----------------

export interface DiaryEntry {
  id: number;
  date: IsoDate;
  meal: Meal;
  name: string;
  amount: string | null;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  ingredientId: number | null;
  recipeId: number | null;
}

export interface AddDiaryRequest {
  date: IsoDate;
  meal: Meal;
  ingredientId?: number | null;
  amountGrams?: number | null;
  recipeId?: number | null;
  servings?: number | null;
  customName?: string | null;
  customKcal?: number | null;
  customProtein?: number | null;
  customCarbs?: number | null;
  customFat?: number | null;
  customFiber?: number | null;
}

export interface MoveOrCopyDiaryRequest {
  date: IsoDate;
  meal: Meal;
}

export interface WaterEntry {
  id: number;
  ml: number;
}

export interface WeightEntry {
  id: number;
  date: IsoDate;
  weightKg: number | null;
}

// ---------------- day summary ----------------

export interface MealBlock {
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  entries: DiaryEntry[];
}

export interface WaterSummary {
  targetMl: number;
  totalMl: number;
  entries: WaterEntry[];
}

export interface DaySummary {
  date: IsoDate;
  dailyCalorieTarget: number | null;
  consumedKcal: number | null;
  remainingKcal: number | null;
  totalProtein: number;
  totalCarbs: number;
  totalFat: number;
  totalFiber: number;
  proteinTargetG: number | null;
  carbsTargetG: number | null;
  fatTargetG: number | null;
  fiberTargetG: number | null;
  byMeal: Partial<Record<Meal, MealBlock>>;
  water: WaterSummary;
}

// ---------------- reports ----------------

export interface DailyNutritionPoint {
  date: IsoDate;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  kcalTarget: number | null;
  proteinTarget: number | null;
  carbsTarget: number | null;
  fatTarget: number | null;
  fiberTarget: number | null;
}

export interface DailyWaterPoint {
  date: IsoDate;
  ml: number;
  mlTarget: number | null;
}

// ---------------- AI ----------------

export interface ParsedIngredient {
  name: string;
  quantity: string | null;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
}

export interface ParseResult {
  items: ParsedIngredient[];
  totalCalories: number;
  totalProtein: number;
  totalCarbs: number;
  totalFat: number;
  totalFiber: number;
}

export interface ParsedRecipeIngredient {
  name: string;
  kcalPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
  fiberPer100g: number;
  amountGrams: number;
}

export interface ParsedRecipe {
  name: string;
  servings: number | null;
  ingredients: ParsedRecipeIngredient[];
  /** Only sent on the way back up to /api/recipes/from-ai. */
  totalCookedGrams?: number | null;
}
