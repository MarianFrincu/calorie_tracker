package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import java.util.List;

/**
 * Strategy for turning a free-text meal description into structured data.
 * Two flavors:
 *   - {@link #parse(String)}       returns flat items intended for diary entries.
 *   - {@link #parseRecipe(String)} returns a recipe blueprint (per-100g macros + grams used).
 * Implementations selected at runtime via {@code calorietracker.ai.provider}:
 *   - {@code library} -> {@link LibraryIngredientParser} (food-library search; default, free)
 *   - {@code gemini}  -> {@link GeminiIngredientParser} (Google Gemini API)
 *   - {@code mock}    -> {@link MockIngredientParser} (built-in table; tests)
 */
public interface IngredientParser {
    List<ParsedIngredient> parse(String text);
    ParsedRecipe parseRecipe(String text);
}
