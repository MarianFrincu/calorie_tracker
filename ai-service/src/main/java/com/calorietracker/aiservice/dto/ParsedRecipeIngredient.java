package com.calorietracker.aiservice.dto;

public record ParsedRecipeIngredient(
        String name,
        int kcalPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        double fiberPer100g,
        double amountGrams) {
}
