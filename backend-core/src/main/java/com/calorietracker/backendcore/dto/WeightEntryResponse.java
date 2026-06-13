package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.WeightLog;
import java.time.LocalDate;

public record WeightEntryResponse(Long id, LocalDate date, Double weightKg) {
    public static WeightEntryResponse of(WeightLog w) {
        return new WeightEntryResponse(w.getId(), w.getLogDate(), w.getWeightKg());
    }
}
