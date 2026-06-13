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
        boolean isPublic,
        List<Line> ingredients
) {
    public record Line(Long ingredientId, String ingredientName, double amountGrams) {
    }

    public static RecipeResponse of(Recipe r) {
        List<Line> lines = r.getIngredients().stream()
                .map(ri -> new Line(ri.getIngredient().getId(), ri.getIngredient().getName(), ri.getAmountGrams()))
                .toList();
        return new RecipeResponse(r.getId(), r.getName(), r.getServings(),
                r.getTotalKcal(), r.getTotalProtein(), r.getTotalCarbs(), r.getTotalFat(), r.getTotalFiber(),
                r.getOwnerUserId() == null, lines);
    }
}
