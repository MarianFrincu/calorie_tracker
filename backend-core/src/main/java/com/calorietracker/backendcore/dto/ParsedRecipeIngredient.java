package com.calorietracker.backendcore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Wire shape of one ingredient inside an AI-generated recipe blueprint, as
 * delivered to POST /api/recipes/from-ai by the JavaFX client. The same record
 * exists in {@code ai-service} - the two services exchange JSON, not code.
 */
public record ParsedRecipeIngredient(
        @NotBlank @Size(max = 200) String name,
        @PositiveOrZero int kcalPer100g,
        @PositiveOrZero double proteinPer100g,
        @PositiveOrZero double carbsPer100g,
        @PositiveOrZero double fatPer100g,
        @PositiveOrZero double fiberPer100g,
        @Positive double amountGrams) {
}
