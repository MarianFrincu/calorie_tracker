package com.calorietracker.desktop.model;

public record ParsedIngredient(
        String name,
        String quantity,
        int calories,
        double protein,
        double carbs,
        double fat,
        double fiber
) {
}
