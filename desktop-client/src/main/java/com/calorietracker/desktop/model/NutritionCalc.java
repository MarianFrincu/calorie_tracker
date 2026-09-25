package com.calorietracker.desktop.model;

/**
 * Client-side mirror of the backend NutritionCalculator: BMR / TDEE / macro
 * grams. Used by the Profile + Objective views to preview values live as the
 * user changes inputs without round-tripping to the server.
 */
public final class NutritionCalc {

    /** More protein than this per kg of body weight has no extra benefit. */
    public static final double MAX_PROTEIN_G_PER_KG = 2.2;

    private NutritionCalc() {}

    // ---------------- core ----------------

    public static Integer bmr(Sex sex, Integer age, Double heightCm, Double weightKg) {
        if (sex == null || age == null || heightCm == null || weightKg == null) return null;
        double bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age;
        bmr += (sex == Sex.MALE) ? 5.0 : -161.0;
        return (int) Math.round(bmr);
    }

    public static Integer tdeeMaintain(Sex sex, Integer age, Double heightCm, Double weightKg, ActivityLevel a) {
        Integer bmr = bmr(sex, age, heightCm, weightKg);
        if (bmr == null || a == null) return null;
        return (int) Math.round(bmr * multiplier(a));
    }

    /** TDEE adjusted by the goal percentage (clamped to 0-50), before the BMR floor. */
    public static int adjusted(int tdee, Goal goal, int goalPercent) {
        int pct = Math.max(0, Math.min(50, goalPercent));
        double adj = switch (goal) {
            case LOSE -> -pct / 100.0;
            case GAIN -> +pct / 100.0;
            case MAINTAIN -> 0.0;
        };
        return (int) Math.round(tdee * (1.0 + adj));
    }

    /** The daily calorie target, never below BMR (the backend enforces the same floor). */
    public static int target(int tdee, Goal goal, int goalPercent, int bmr) {
        return Math.max(adjusted(tdee, goal, goalPercent), bmr);
    }

    /** Whether cutting {@code percent} of TDEE would drop below BMR. */
    public static boolean belowBmr(int tdee, int bmr, int percent) {
        return adjusted(tdee, Goal.LOSE, percent) < bmr;
    }

    public record MacroGrams(int protein, int carbs, int fat) {}

    /**
     * Macro grams for a calorie target, with protein capped at 2.2 g per kg of
     * body weight (no cap when the weight is unknown); the calories above the
     * cap go to carbs and fat in the preset's proportion. Same maths as the
     * backend's NutritionCalculator.
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

    /** True when the preset would ask for more protein than 2.2 g/kg. */
    public static boolean proteinCapped(int calories, MacroPreset preset, Double weightKg) {
        if (weightKg == null || weightKg <= 0) return false;
        MacroPreset p = preset == null ? MacroPreset.BALANCED : preset;
        return calories * p.proteinPercent / 100.0 > MAX_PROTEIN_G_PER_KG * weightKg * 4.0;
    }

    private static double multiplier(ActivityLevel a) {
        return switch (a) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
            case VERY_ACTIVE -> 1.9;
        };
    }
}
