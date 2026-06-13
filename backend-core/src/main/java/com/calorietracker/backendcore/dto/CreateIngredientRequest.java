package com.calorietracker.backendcore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateIngredientRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String brand,
        // Pure fats top out around 900 kcal/100 g, so 1500 is comfortably above any real food.
        @PositiveOrZero @Max(1500) int kcalPer100g,
        @PositiveOrZero @Max(100) double proteinPer100g,
        @PositiveOrZero @Max(100) double carbsPer100g,
        @PositiveOrZero @Max(100) double fatPer100g,
        @PositiveOrZero @Max(100) double fiberPer100g
) {
}
