package com.calorietracker.desktop.model;

import java.util.List;

public record CreateRecipeRequest(
        String name,
        int servings,
        List<Line> ingredients,
        /** Weight of the cooked dish in grams. 0 means "fall back to raw sum" on the server. */
        double totalCookedGrams
) {
    public record Line(Long ingredientId, double amountGrams) {
    }
}
