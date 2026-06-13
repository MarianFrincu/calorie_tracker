package com.calorietracker.aiservice.ai;

import java.util.List;

/** Target type for Spring AI structured output when generating a recipe. */
public record LlmRecipe(String name, Integer servings, List<LlmRecipeIngredient> ingredients) {
    public record LlmRecipeIngredient(
            String name,
            int kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            double fiberPer100g,
            double amountGrams) {
    }
}
