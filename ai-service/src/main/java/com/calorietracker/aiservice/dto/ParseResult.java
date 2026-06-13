package com.calorietracker.aiservice.dto;

import java.util.List;

public record ParseResult(
        List<ParsedIngredient> items,
        int totalCalories,
        double totalProtein,
        double totalCarbs,
        double totalFat,
        double totalFiber) {

    public static ParseResult of(List<ParsedIngredient> items) {
        int kcal = 0;
        double protein = 0, carbs = 0, fat = 0, fiber = 0;
        for (ParsedIngredient i : items) {
            kcal    += i.calories();
            protein += i.protein();
            carbs   += i.carbs();
            fat     += i.fat();
            fiber   += i.fiber();
        }
        return new ParseResult(items, kcal, r(protein), r(carbs), r(fat), r(fiber));
    }

    private static double r(double v) { return Math.round(v * 10.0) / 10.0; }
}
