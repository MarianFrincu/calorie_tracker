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
 * from {@code goal} + {@code goalPercent} (e.g. LOSE 20 → ×0.80) - but never
 * below BMR: eating less than the body burns at rest isn't a safe diet.
 *
 * <p>Macro grams come from the {@link MacroPreset} percentages applied to the
 * calorie target (protein/carbs at 4 kcal/g, fat at 9 kcal/g), with protein
 * capped at {@value #MAX_PROTEIN_G_PER_KG} g per kg of body weight. Fiber is
 * tracked separately on the user (default 30 g/day).
 *
 * <p>Mirrored for live previews in web-client/src/lib/nutrition.ts and the
 * desktop client's NutritionCalc - keep all three in step.
 */
public final class NutritionCalculator {

    /** More protein than this has no extra benefit and crowds out carbs and fat. */
    public static final double MAX_PROTEIN_G_PER_KG = 2.2;

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
     * Final calorie target = TDEE × (1 + signedPercent/100), never below BMR.
     * Returns null if any input is missing.
     *
     * @param goalPercent magnitude, e.g. 20 (clamped to [0, 50])
     */
    public static Integer dailyCalorieTarget(AppUser u, Goal goal, Integer goalPercent) {
        Integer tdee = tdeeMaintain(u);
        if (tdee == null || goal == null) return null;
        int target = adjust(tdee, goal, goalPercent);
        return Math.max(target, bmr(u));
    }

    private static int adjust(int tdee, Goal goal, Integer goalPercent) {
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
     *
     * <p>Protein is capped at {@value #MAX_PROTEIN_G_PER_KG} g/kg of
     * {@code weightKg} (no cap when it's unknown). The calories above the cap go
     * to carbs and fat in the preset's own proportion - so keto stays low-carb -
     * and the total stays the same.
     */
    public static MacroGrams macroGrams(int calories, MacroPreset preset, Double weightKg) {
        if (preset == null) preset = MacroPreset.BALANCED;
        double proteinKcal = calories * preset.proteinPercent / 100.0;
        double carbsKcal   = calories * preset.carbsPercent   / 100.0;
        double fatKcal     = calories * preset.fatPercent     / 100.0;
        if (weightKg != null && weightKg > 0) {
            double maxProteinKcal = MAX_PROTEIN_G_PER_KG * weightKg * 4.0;
            if (proteinKcal > maxProteinKcal) {
                double spare = proteinKcal - maxProteinKcal;
                int rest = preset.carbsPercent + preset.fatPercent;
                proteinKcal = maxProteinKcal;
                carbsKcal += spare * preset.carbsPercent / rest;
                fatKcal   += spare * preset.fatPercent / rest;
            }
        }
        return new MacroGrams((int) Math.round(proteinKcal / 4.0),
                (int) Math.round(carbsKcal / 4.0),
                (int) Math.round(fatKcal / 9.0));
    }
}
