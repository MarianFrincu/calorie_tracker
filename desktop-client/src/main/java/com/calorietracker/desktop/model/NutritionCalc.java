package com.calorietracker.desktop.model;

/**
 * Client-side mirror of the backend NutritionCalculator: BMR / TDEE / macro
 * grams. Used by the Profile + Objective views to preview values live as the
 * user changes inputs without round-tripping to the server.
 */
public final class NutritionCalc {

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

    public static Integer dailyCalorieTarget(Sex sex, Integer age, Double heightCm, Double weightKg,
                                             ActivityLevel a, Goal goal, Integer goalPercent) {
        Integer tdee = tdeeMaintain(sex, age, heightCm, weightKg, a);
        if (tdee == null || goal == null) return null;
        int pct = goalPercent == null ? 0 : Math.max(0, Math.min(50, goalPercent));
        double adj = switch (goal) {
            case LOSE -> -pct / 100.0;
            case GAIN -> +pct / 100.0;
            case MAINTAIN -> 0.0;
        };
        return (int) Math.round(tdee * (1.0 + adj));
    }

    public record MacroGrams(int protein, int carbs, int fat) {}

    public static MacroGrams macroGrams(int calories, MacroPreset preset) {
        if (preset == null) preset = MacroPreset.BALANCED;
        int protein = (int) Math.round(calories * (preset.proteinPercent / 100.0) / 4.0);
        int carbs   = (int) Math.round(calories * (preset.carbsPercent   / 100.0) / 4.0);
        int fat     = (int) Math.round(calories * (preset.fatPercent     / 100.0) / 9.0);
        return new MacroGrams(protein, carbs, fat);
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
