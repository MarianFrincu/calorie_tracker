package com.calorietracker.backendcore.dto;

import com.calorietracker.backendcore.model.Ingredient;

public record IngredientResponse(
        Long id,
        String name,
        String brand,
        int kcalPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        double fiberPer100g,
        boolean isPublic
) {
    public static IngredientResponse of(Ingredient i) {
        return new IngredientResponse(i.getId(), i.getName(), i.getBrand(),
                i.getKcalPer100g(), i.getProteinPer100g(), i.getCarbsPer100g(),
                i.getFatPer100g(), i.getFiberPer100g(),
                i.getOwnerUserId() == null);
    }
}
