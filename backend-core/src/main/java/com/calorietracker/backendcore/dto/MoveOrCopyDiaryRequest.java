package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Meal;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Where to drop the entry. Used by both move and copy endpoints. */
public record MoveOrCopyDiaryRequest(
        @NotNull
        @PlausibleDate
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,
        @NotNull Meal meal) {
}
