package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import com.calorietracker.backendcore.model.ObjectiveHistory;

/**
 * The user's current (or historical) objective: direction + magnitude + macro
 * split + derived per-day targets.
 */
public record ObjectiveResponse(
        Goal goal,
        Integer goalPercent,
        MacroPreset macroPreset,
        Integer dailyCalorieTarget,
        Integer dailyProteinTargetG,
        Integer dailyCarbsTargetG,
        Integer dailyFatTargetG,
        Integer dailyFiberTargetG,
        Integer dailyWaterTargetMl
) {
    public static ObjectiveResponse of(AppUser u) {
        return new ObjectiveResponse(
                u.getGoal(), u.getGoalPercent(), u.getMacroPreset(),
                u.getDailyCalorieTarget(),
                u.getDailyProteinTargetG(),
                u.getDailyCarbsTargetG(),
                u.getDailyFatTargetG(),
                u.getDailyFiberTargetG(),
                u.getDailyWaterTargetMl());
    }

    public static ObjectiveResponse of(ObjectiveHistory h) {
        return new ObjectiveResponse(
                h.getGoal(), h.getGoalPercent(), h.getMacroPreset(),
                h.getDailyCalorieTarget(),
                h.getDailyProteinTargetG(),
                h.getDailyCarbsTargetG(),
                h.getDailyFatTargetG(),
                h.getDailyFiberTargetG(),
                h.getDailyWaterTargetMl());
    }
}
