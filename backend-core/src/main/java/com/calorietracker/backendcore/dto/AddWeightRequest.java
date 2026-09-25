package com.calorietracker.backendcore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Upserts the weight for a given date (one entry per user per day). */
public record AddWeightRequest(
        @NotNull @PlausibleDate @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        @NotNull @DecimalMin("20.0") @DecimalMax("500.0") Double weightKg
) {
}
