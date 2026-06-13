package com.calorietracker.aiservice.dto;

import java.util.List;

public record ParsedRecipe(String name, Integer servings, List<ParsedRecipeIngredient> ingredients) {
}
