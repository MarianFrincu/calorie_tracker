package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Recipe;
import java.util.List;

public record RecipeResponse(
        Long id,
        String name,
        int servings,
        int totalKcal,
        double totalProtein,
        double totalCarbs,
        double totalFat,
        double totalFiber,
        /** Weight of the cooked dish in grams. Zero means "use raw weight" — clients should fall back to summing line.amountGrams. */
        double totalCookedGrams,
        boolean isPublic,
        List<Line> ingredients
) {
    public record Line(Long ingredientId, String ingredientName, double amountGrams) {
    }

    public static RecipeResponse of(Recipe r) {
        List<Line> lines = r.getIngredients().stream()
                .map(ri -> new Line(ri.getIngredient().getId(), ri.getIngredient().getName(), ri.getAmountGrams()))
                .toList();
        double cooked = r.getTotalCookedGrams() == null ? 0.0 : r.getTotalCookedGrams();
        return new RecipeResponse(r.getId(), r.getName(), r.getServings(),
                r.getTotalKcal(), r.getTotalProtein(), r.getTotalCarbs(), r.getTotalFat(), r.getTotalFiber(),
                cooked, r.getOwnerUserId() == null, lines);
    }
}
