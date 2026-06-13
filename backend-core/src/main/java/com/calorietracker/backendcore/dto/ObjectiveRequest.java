package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Goal;
import com.calorietracker.backendcore.model.MacroPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * What the user wants their objective to be from today onward. Goal/percent
 * combine to a calorie target relative to TDEE (e.g. LOSE 20 = -20%). Macro
 * preset drives the protein/carbs/fat split. Fiber + water targets are
 * standalone overrides (defaults: 30 g, 2000 ml).
 */
public record ObjectiveRequest(
        Goal goal,
        @Min(0) @Max(50) Integer goalPercent,
        MacroPreset macroPreset,
        @Positive Integer dailyFiberTargetG,
        @Positive Integer dailyWaterTargetMl
) {
}
