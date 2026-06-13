package com.calorietracker.desktop.model;

import java.util.List;

public record CreateRecipeRequest(
        String name,
        int servings,
        List<Line> ingredients
) {
    public record Line(Long ingredientId, double amountGrams) {
    }
}
