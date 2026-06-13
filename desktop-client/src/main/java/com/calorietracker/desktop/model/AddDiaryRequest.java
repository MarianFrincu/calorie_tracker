package com.calorietracker.desktop.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Send exactly one of: ingredientId+amountGrams, recipeId+servings, or customName+macros. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddDiaryRequest(
        String date,           // "yyyy-MM-dd"
        Meal meal,
        Long ingredientId,
        Double amountGrams,
        Long recipeId,
        Double servings,
        String customName,
        Integer customKcal,
        Double customProtein,
        Double customCarbs,
        Double customFat,
        Double customFiber
) {
    public static AddDiaryRequest ingredient(String date, Meal m, long id, double grams) {
        return new AddDiaryRequest(date, m, id, grams, null, null, null, null, null, null, null, null);
    }
    public static AddDiaryRequest recipe(String date, Meal m, long id, double servings) {
        return new AddDiaryRequest(date, m, null, null, id, servings, null, null, null, null, null, null);
    }
    public static AddDiaryRequest custom(String date, Meal m, String name, int kcal,
                                         double protein, double carbs, double fat, double fiber) {
        return new AddDiaryRequest(date, m, null, null, null, null, name, kcal, protein, carbs, fat, fiber);
    }
}
