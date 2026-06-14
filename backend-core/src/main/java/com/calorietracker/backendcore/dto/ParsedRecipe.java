package com.calorietracker.backendcore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Wire shape of an AI-generated recipe blueprint, as delivered to
 * POST /api/recipes/from-ai by the JavaFX client. {@code ai-service} produces
 * the same record from POST /api/ai/parse-recipe.
 * <p>
 * Validation here is the last line of defense: the JavaFX client always sends
 * sensible payloads, but anything that POSTs here with a valid JWT could send
 * pathological input (empty list, 10k items, etc).
 */
public record ParsedRecipe(
        @NotBlank @Size(max = 200) String name,
        Integer servings,
        @NotEmpty @Size(max = 100) @Valid List<ParsedRecipeIngredient> ingredients,
        /** Cooked-weight in grams (optional). 0 / null → server falls back to raw sum. */
        @PositiveOrZero @Max(100_000) Double totalCookedGrams) {
}
