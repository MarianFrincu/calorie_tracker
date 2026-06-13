package com.calorietracker.backendcore.dto;

import java.time.LocalDate;

/**
 * One day's actual intake + the targets that were in effect on that day. The
 * desktop charts use this to draw "actual" bars next to a "target" line.
 */
public record DailyNutritionPoint(
        LocalDate date,
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
