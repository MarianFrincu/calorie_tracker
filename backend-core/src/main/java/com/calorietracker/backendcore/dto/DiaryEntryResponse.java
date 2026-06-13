package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.DiaryEntry;
import com.calorietracker.backendcore.model.Meal;
import java.time.LocalDate;

public record DiaryEntryResponse(
        Long id,
        LocalDate date,
        Meal meal,
        String name,
        String amount,
        int kcal,
        double protein,
        double carbs,
        double fat,
        double fiber,
        Long ingredientId,
        Long recipeId
) {
    public static DiaryEntryResponse of(DiaryEntry e) {
        return new DiaryEntryResponse(e.getId(), e.getEntryDate(), e.getMeal(),
                e.getDisplayName(), e.getAmountText(),
                e.getKcal(), e.getProtein(), e.getCarbs(), e.getFat(), e.getFiber(),
                e.getIngredientId(), e.getRecipeId());
    }
}
