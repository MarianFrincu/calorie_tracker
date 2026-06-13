package com.calorietracker.desktop.model;

import java.util.List;

/**
 * AI recipe blueprint. Returned by POST /api/ai/parse-recipe (ai-service), then
 * sent (possibly with edits) to POST /api/recipes/from-ai (backend-core) to
 * persist.
 */
public record ParsedRecipe(
        String name,
        Integer servings,
        List<ParsedRecipeIngredient> ingredients
) {
}
