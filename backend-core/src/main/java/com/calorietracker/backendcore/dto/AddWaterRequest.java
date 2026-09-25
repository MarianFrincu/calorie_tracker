package com.calorietracker.backendcore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AddWaterRequest(
        @NotNull @PlausibleDate @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        // 10 litres in one entry is already absurd; the cap also keeps the
        // per-day int sum far from overflow.
        @Positive @Max(10_000) int ml
) {
}
