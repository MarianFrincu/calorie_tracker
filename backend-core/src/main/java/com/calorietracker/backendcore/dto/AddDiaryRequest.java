package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Meal;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Add a diary entry. Provide EITHER {@code ingredientId}+{@code amountGrams},
 * OR {@code recipeId}+{@code servings}, OR a freeform {@code customName} with
 * macros (used by the AI "Add all" flow).
 */
public record AddDiaryRequest(
        @NotNull @PlausibleDate @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        @NotNull Meal meal,
        Long ingredientId,
        @Positive @Max(100_000) Double amountGrams,
        Long recipeId,
        @Positive @Max(1000) Double servings,
        @Size(max = 200) String customName,
        @PositiveOrZero @Max(50_000) Integer customKcal,
        @PositiveOrZero @Max(10_000) Double customProtein,
        @PositiveOrZero @Max(10_000) Double customCarbs,
        @PositiveOrZero @Max(10_000) Double customFat,
        @PositiveOrZero @Max(10_000) Double customFiber
) {
}
