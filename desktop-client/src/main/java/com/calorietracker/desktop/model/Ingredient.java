package com.calorietracker.desktop.model;

public record Ingredient(
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
}
