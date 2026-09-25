package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, fully offline parser backed by a small built-in table.
 * Used for tests and when ai-service runs on its own; the {@code library}
 * provider also falls back to it for foods the library doesn't know.
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "mock")
public class MockIngredientParser implements IngredientParser {

    /** Per single portion: {kcal, protein g, carbs g, fat g, fiber g, typical portion grams}. */
    private static final Map<String, double[]> TABLE = Map.ofEntries(
            Map.entry("egg",     new double[]{72,  6.3,  0.4, 4.8,  0,    50}),
            Map.entry("toast",   new double[]{75,  2.6,  13,  1,    1.1,  30}),
            Map.entry("bread",   new double[]{75,  2.6,  13,  1,    1.1,  30}),
            Map.entry("butter",  new double[]{102, 0.1,  0,   11.5, 0,    14}),
            Map.entry("rice",    new double[]{206, 4.3,  45,  0.4,  0.6,  158}),
            Map.entry("chicken", new double[]{165, 31,   0,   3.6,  0,    100}),
            Map.entry("banana",  new double[]{105, 1.3,  27,  0.4,  3.1,  118}),
            Map.entry("apple",   new double[]{95,  0.5,  25,  0.3,  4.4,  182}),
            Map.entry("milk",    new double[]{103, 8,    12,  2.4,  0,    244}),
            Map.entry("oats",    new double[]{150, 5,    27,  2.5,  4,    40}),
            Map.entry("salmon",  new double[]{208, 20,   0,   13,   0,    100}),
            Map.entry("avocado", new double[]{240, 3,    12,  22,   10,   150}),
            Map.entry("cheese",  new double[]{113, 7,    0.4, 9,    0,    28}),
            Map.entry("yogurt",  new double[]{100, 10,   6,   4,    0,    170})
    );

    /** Typical single-piece weights for common foods not in TABLE (count -> grams). */
    private static final Map<String, Double> EXTRA_PORTIONS = Map.ofEntries(
            Map.entry("potato", 150.0), Map.entry("orange", 130.0), Map.entry("tomato", 120.0),
            Map.entry("pear", 180.0), Map.entry("carrot", 60.0), Map.entry("cucumber", 200.0),
            Map.entry("pasta", 180.0), Map.entry("steak", 200.0), Map.entry("tuna", 100.0),
            Map.entry("almond", 1.2), Map.entry("walnut", 4.0), Map.entry("strawberr", 12.0),
            Map.entry("peach", 150.0), Map.entry("kiwi", 75.0), Map.entry("bagel", 100.0)
    );

    /** Unknown food: 100 kcal per 100 g "portion". */
    private static final double[] DEFAULT_MACROS = {100, 5, 15, 3, 2, 100};

    @Override
    public List<ParsedIngredient> parse(String text) {
        return MealText.split(text).stream().map(MockIngredientParser::estimate).toList();
    }

    @Override
    public ParsedRecipe parseRecipe(String text) {
        if (text == null || text.isBlank()) return new ParsedRecipe("My recipe", 1, List.of());
        List<ParsedRecipeIngredient> ingredients =
                MealText.split(text).stream().map(MockIngredientParser::estimateRecipeLine).toList();
        return new ParsedRecipe(suggestedRecipeName(text), 1, ingredients);
    }

    /** Diary-style estimate for one item from the built-in table. */
    static ParsedIngredient estimate(MealText.Item item) {
        Match m = match(item.name());
        double portions = item.grams() != null ? item.grams() / m.macros[5] : item.count();
        return new ParsedIngredient(
                m.name, quantityText(item),
                (int) Math.round(m.macros[0] * portions),
                round(m.macros[1] * portions),
                round(m.macros[2] * portions),
                round(m.macros[3] * portions),
                round(m.macros[4] * portions));
    }

    /** Recipe-style estimate (per-100 g values + grams used) for one item. */
    static ParsedRecipeIngredient estimateRecipeLine(MealText.Item item) {
        Match m = match(item.name());
        double portionGrams = m.macros[5];
        double totalGrams = item.grams() != null ? item.grams() : portionGrams * item.count();
        return new ParsedRecipeIngredient(
                m.name,
                (int) Math.round(m.macros[0] / portionGrams * 100.0),
                round(m.macros[1] / portionGrams * 100.0),
                round(m.macros[2] / portionGrams * 100.0),
                round(m.macros[3] / portionGrams * 100.0),
                round(m.macros[4] / portionGrams * 100.0),
                totalGrams);
    }

    /** Grams of one typical piece/serving of {@code name}; 100 g when unknown. */
    static double portionGrams(String name) {
        for (Map.Entry<String, double[]> e : TABLE.entrySet()) {
            if (name.contains(e.getKey())) return e.getValue()[5];
        }
        for (Map.Entry<String, Double> e : EXTRA_PORTIONS.entrySet()) {
            if (name.contains(e.getKey())) return e.getValue();
        }
        return 100.0;
    }

    static String quantityText(MealText.Item item) {
        if (item.grams() == null) return String.valueOf(item.count());
        double g = item.grams();
        return (g == Math.floor(g) ? String.valueOf((long) g) : String.valueOf(round(g))) + " g";
    }

    static String suggestedRecipeName(String text) {
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 48) return MealText.capitalize(trimmed);
        return MealText.capitalize(trimmed.substring(0, 45)) + "...";
    }

    static double round(double v) { return Math.round(v * 10.0) / 10.0; }

    private record Match(String name, double[] macros) {}

    private static Match match(String name) {
        for (Map.Entry<String, double[]> e : TABLE.entrySet()) {
            if (name.contains(e.getKey())) return new Match(MealText.capitalize(e.getKey()), e.getValue());
        }
        return new Match(MealText.capitalize(name), DEFAULT_MACROS);
    }
}
