package com.calorietracker.desktop.model;

import java.util.List;

public record Recipe(
        Long id,
        String name,
        int servings,
        int totalKcal,
        double totalProtein,
        double totalCarbs,
        double totalFat,
        double totalFiber,
        /** Weight of the cooked dish in grams; 0 means "fall back to raw weight". */
        double totalCookedGrams,
        boolean isPublic,
        List<Line> ingredients
) {
    public record Line(Long ingredientId, String ingredientName, double amountGrams) {
    }

    /** Effective cooked weight for per-100g math (raw sum when cooked weight wasn't recorded). */
    public double effectiveCookedGrams() {
        if (totalCookedGrams > 0) return totalCookedGrams;
        return ingredients == null ? 0
                : ingredients.stream().mapToDouble(Line::amountGrams).sum();
    }

    public double kcalPer100g() {
        double g = effectiveCookedGrams();
        return g <= 0 ? 0 : totalKcal * 100.0 / g;
    }
    public double proteinPer100g() {
        double g = effectiveCookedGrams();
        return g <= 0 ? 0 : totalProtein * 100.0 / g;
    }
    public double carbsPer100g() {
        double g = effectiveCookedGrams();
        return g <= 0 ? 0 : totalCarbs * 100.0 / g;
    }
    public double fatPer100g() {
        double g = effectiveCookedGrams();
        return g <= 0 ? 0 : totalFat * 100.0 / g;
    }
    public double fiberPer100g() {
        double g = effectiveCookedGrams();
        return g <= 0 ? 0 : totalFiber * 100.0 / g;
    }
}
