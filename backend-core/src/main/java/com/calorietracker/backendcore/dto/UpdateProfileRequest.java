package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.ActivityLevel;
import com.calorietracker.backendcore.model.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body stats only. Goal/macro fields live in {@link ObjectiveRequest} now.
 * All fields are optional - send only what you want to change.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        Sex sex,
        @Min(5) @Max(120) Integer age,
        @Positive @DecimalMax("300.0") Double heightCm,
        @Positive @DecimalMax("500.0") Double weightKg,
        ActivityLevel activityLevel
) {
}
