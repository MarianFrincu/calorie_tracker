package com.calorietracker.desktop.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Body stats only - objective fields go through UpdateObjectiveRequest. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileRequest(
        String displayName,
        Sex sex,
        Integer age,
        Double heightCm,
        Double weightKg,
        ActivityLevel activityLevel
) {
}
