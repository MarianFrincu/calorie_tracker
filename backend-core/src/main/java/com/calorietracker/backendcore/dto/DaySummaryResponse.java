package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Meal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the day view needs in one round-trip. The four macro targets
 * come from the objective in effect on {@code date}, so navigating to a past
 * day shows that day's frozen targets rather than today's.
 */
public record DaySummaryResponse(
        LocalDate date,
        Integer dailyCalorieTarget,
        Integer consumedKcal,
        Integer remainingKcal,
        double totalProtein,
        double totalCarbs,
        double totalFat,
        double totalFiber,
        Integer proteinTargetG,
        Integer carbsTargetG,
        Integer fatTargetG,
        Integer fiberTargetG,
        Map<Meal, MealBlock> byMeal,
        WaterSummary water
) {
    public record MealBlock(int kcal, double protein, double carbs, double fat, double fiber,
                            List<DiaryEntryResponse> entries) {
    }

    public record WaterSummary(int targetMl, int totalMl, List<WaterEntryResponse> entries) {
    }

    public record WaterEntryResponse(Long id, int ml) {
    }
}
