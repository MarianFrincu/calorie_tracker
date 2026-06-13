package com.calorietracker.desktop.model;

/** Body stats + derived BMR / TDEE. Mirrors backend ProfileResponse. */
public record Profile(
        Long id,
        String userKey,
        String displayName,
        Sex sex,
        Integer age,
        Double heightCm,
        Double weightKg,
        ActivityLevel activityLevel,
        Integer bmr,
        Integer tdeeMaintain
) {
}
