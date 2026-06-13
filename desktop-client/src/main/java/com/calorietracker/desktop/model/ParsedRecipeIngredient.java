package com.calorietracker.desktop.model;

/** Mirror of backend ParsedRecipeIngredient: per-100g macros + grams used. */
public record ParsedRecipeIngredient(
        String name,
        int kcalPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        double fiberPer100g,
        double amountGrams
) {
}
