package com.calorietracker.desktop.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateObjectiveRequest(
        Goal goal,
        Integer goalPercent,
        MacroPreset macroPreset,
        Integer dailyFiberTargetG,
        Integer dailyWaterTargetMl
) {
}
