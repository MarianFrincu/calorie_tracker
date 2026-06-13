package com.calorietracker.desktop.model;

/**
 * One day's actual intake + the targets that were in effect on that day.
 * Mirrors backend DailyNutritionPoint.
 */
public record DailyNutritionPoint(
        String date,
        int kcal,
        double protein,
        double carbs,
        double fat,
        double fiber,
        Integer kcalTarget,
        Integer proteinTarget,
        Integer carbsTarget,
        Integer fatTarget,
        Integer fiberTarget
) {
}
