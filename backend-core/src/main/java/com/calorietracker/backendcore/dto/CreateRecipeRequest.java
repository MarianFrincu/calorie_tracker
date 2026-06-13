package com.calorietracker.backendcore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 200) String name,
        @Positive @Max(1000) Integer servings,
        // Cap recipe size to keep the createFromAi / persist loop bounded.
        @NotEmpty @Size(max = 100) @Valid List<Line> ingredients
) {
    public record Line(
            @Positive Long ingredientId,
            @Positive @Max(100_000) Double amountGrams
    ) {
    }
}
