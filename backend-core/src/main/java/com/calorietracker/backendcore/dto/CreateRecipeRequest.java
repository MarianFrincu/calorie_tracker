package com.calorietracker.backendcore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 200) String name,
        @Positive @Max(1000) Integer servings,
        // Cap recipe size to keep the createFromAi / persist loop bounded.
        @NotEmpty @Size(max = 100) @Valid List<Line> ingredients,
        // Weight of the cooked dish in grams (user-provided to capture cooking
        // losses / hydration). 0 means "fall back to raw weight" for per-100g
        // math; clients are encouraged to send a real value.
        @PositiveOrZero @Max(100_000) Double totalCookedGrams
) {
    public record Line(
            @NotNull @Positive Long ingredientId,
            @NotNull @Positive @Max(100_000) Double amountGrams
    ) {
    }
}
