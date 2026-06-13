package com.calorietracker.desktop.model;

import java.util.List;

public record ParseResult(
        List<ParsedIngredient> items,
        int totalCalories,
        double totalProtein,
        double totalCarbs,
        double totalFat,
        double totalFiber
) {
}
