package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.model.ActivityLevel;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import com.calorietracker.backendcore.model.Sex;

/**
 * BMR + TDEE + percent-based goal + macro distribution maths.
 *
 * <p>BMR uses Mifflin-St Jeor. TDEE = BMR × activity multiplier. The calorie
 * target is then TDEE × (1 + goalAdjustment), where the adjustment is derived
 * from {@code goal} + {@code goalPercent} (e.g. LOSE 20 → ×0.80).
 *
 * <p>Macro grams come from the {@link MacroPreset} percentages applied to the
 * calorie target (protein/carbs at 4 kcal/g, fat at 9 kcal/g). Fiber is
 * tracked separately on the user (default 30 g/day).
 */
public final class NutritionCalculator {

    private NutritionCalculator() {}

    // ---------------- core ----------------

    /** Mifflin-St Jeor BMR. Returns null if any body input is missing. */
    public static Integer bmr(AppUser u) {
        if (u.getSex() == null || u.getAge() == null || u.getHeightCm() == null || u.getWeightKg() == null) {
            return null;
        }
        return bmr(u.getSex(), u.getAge(), u.getHeightCm(), u.getWeightKg());
    }

    public static Integer bmr(Sex sex, Integer age, Double heightCm, Double weightKg) {
        if (sex == null || age == null || heightCm == null || weightKg == null) return null;
        double bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age;
        bmr += (sex == Sex.MALE) ? 5.0 : -161.0;
        return (int) Math.round(bmr);
    }

    /** TDEE (maintenance calories) = BMR × activity multiplier. */
    public static Integer tdeeMaintain(AppUser u) {
        Integer bmr = bmr(u);
        if (bmr == null || u.getActivityLevel() == null) return null;
        return (int) Math.round(bmr * u.getActivityLevel().multiplier);
    }

    public static Integer tdeeMaintain(Sex sex, Integer age, Double heightCm, Double weightKg, ActivityLevel activity) {
        Integer bmr = bmr(sex, age, heightCm, weightKg);
        if (bmr == null || activity == null) return null;
        return (int) Math.round(bmr * activity.multiplier);
    }

    /**
     * Final calorie target = TDEE × (1 + signedPercent/100). Returns null if
     * any input is missing.
     *
     * @param goalPercent magnitude, e.g. 20 (clamped to [0, 50])
     */
    public static Integer dailyCalorieTarget(AppUser u, Goal goal, Integer goalPercent) {
        Integer tdee = tdeeMaintain(u);
        if (tdee == null || goal == null) return null;
        int pct = goalPercent == null ? 0 : Math.max(0, Math.min(50, goalPercent));
        double adj = switch (goal) {
            case LOSE     -> -pct / 100.0;
            case GAIN     -> +pct / 100.0;
            case MAINTAIN -> 0.0;
        };
        return (int) Math.round(tdee * (1.0 + adj));
    }

    // ---------------- macros ----------------

    public record MacroGrams(int protein, int carbs, int fat) {}

    /**
     * Macro grams derived from the calorie target and the preset's
     * protein/carbs/fat percentages. Protein and carbs are 4 kcal/g, fat is 9 kcal/g.
     */
    public static MacroGrams macroGrams(int calories, MacroPreset preset) {
        if (preset == null) preset = MacroPreset.BALANCED;
        int protein = (int) Math.round(calories * (preset.proteinPercent / 100.0) / 4.0);
        int carbs   = (int) Math.round(calories * (preset.carbsPercent   / 100.0) / 4.0);
        int fat     = (int) Math.round(calories * (preset.fatPercent     / 100.0) / 9.0);
        return new MacroGrams(protein, carbs, fat);
    }
}
