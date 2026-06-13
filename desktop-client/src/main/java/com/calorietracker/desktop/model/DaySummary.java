package com.calorietracker.desktop.model;

import java.util.List;
import java.util.Map;

/** Backend's GET /api/summary?date=... payload - everything the day view needs. */
public record DaySummary(
        String date,
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
                            List<DiaryEntry> entries) {
    }
    public record WaterSummary(int targetMl, int totalMl, List<WaterEntry> entries) {
    }
    public record WaterEntry(Long id, int ml) {
    }
}
