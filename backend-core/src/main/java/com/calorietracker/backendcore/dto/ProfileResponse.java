package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.ActivityLevel;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Sex;
import com.calorietracker.backendcore.service.NutritionCalculator;

/**
 * Body stats + derived BMR/TDEE. The objective lives on its own
 * (/api/objective) so it isn't duplicated here.
 */
public record ProfileResponse(
        Long id,
        String userKey,
        String displayName,
        Sex sex,
        Integer age,
        Double heightCm,
        Double weightKg,
        ActivityLevel activityLevel,
        Integer bmr,             // computed
        Integer tdeeMaintain     // computed (calories needed to maintain weight)
) {
    public static ProfileResponse of(AppUser u) {
        return new ProfileResponse(u.getId(), u.getUserKey(), u.getDisplayName(),
                u.getSex(), u.getAge(), u.getHeightCm(), u.getWeightKg(),
                u.getActivityLevel(),
                NutritionCalculator.bmr(u),
                NutritionCalculator.tdeeMaintain(u));
    }
}
