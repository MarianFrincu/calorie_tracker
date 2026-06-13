package com.calorietracker.desktop.model;

public record DiaryEntry(
        Long id,
        String date,
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
}
