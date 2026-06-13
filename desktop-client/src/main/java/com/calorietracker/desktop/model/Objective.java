package com.calorietracker.desktop.model;

public record Objective(
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
}
