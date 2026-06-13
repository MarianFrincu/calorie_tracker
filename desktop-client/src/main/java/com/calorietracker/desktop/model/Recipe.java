package com.calorietracker.desktop.model;

import java.util.List;

public record Recipe(
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
}
