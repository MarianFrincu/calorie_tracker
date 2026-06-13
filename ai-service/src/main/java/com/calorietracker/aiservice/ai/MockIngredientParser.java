package com.calorietracker.aiservice.ai;

import com.calorietracker.aiservice.dto.ParsedIngredient;
import com.calorietracker.aiservice.dto.ParsedRecipe;
import com.calorietracker.aiservice.dto.ParsedRecipeIngredient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline stand-in for the Bedrock-backed parser. Active by
 * default so the stack runs locally with no AWS dependency.
 */
@Component
@ConditionalOnProperty(name = "calorietracker.ai.provider", havingValue = "mock", matchIfMissing = true)
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

    private static final double[] DEFAULT_MACROS = {100, 5, 15, 3, 2, 100};
    private static final Pattern LEADING_QTY = Pattern.compile("(\\d+)");

    @Override
    public List<ParsedIngredient> parse(String text) {
        List<ParsedIngredient> items = new ArrayList<>();
        if (text == null || text.isBlank()) return items;
        for (Parsed p : splitAndMatch(text)) {
            items.add(new ParsedIngredient(
                    p.name, String.valueOf(p.qty),
                    (int) Math.round(p.macros[0] * p.qty),
                    round(p.macros[1] * p.qty),
                    round(p.macros[2] * p.qty),
                    round(p.macros[3] * p.qty),
                    round(p.macros[4] * p.qty)));
        }
        return items;
    }

    @Override
    public ParsedRecipe parseRecipe(String text) {
        if (text == null || text.isBlank()) return new ParsedRecipe("My recipe", 1, List.of());
        List<ParsedRecipeIngredient> ingredients = new ArrayList<>();
        for (Parsed p : splitAndMatch(text)) {
            double portionGrams = p.macros[5];
            double totalGrams = portionGrams * p.qty;
            ingredients.add(new ParsedRecipeIngredient(
                    p.name,
                    (int) Math.round(p.macros[0] / portionGrams * 100.0),
                    round(p.macros[1] / portionGrams * 100.0),
                    round(p.macros[2] / portionGrams * 100.0),
                    round(p.macros[3] / portionGrams * 100.0),
                    round(p.macros[4] / portionGrams * 100.0),
                    totalGrams));
        }
        return new ParsedRecipe(suggestedRecipeName(text), 1, ingredients);
    }

    private static final class Parsed {
        String name;
        int qty;
        double[] macros;
    }

    private List<Parsed> splitAndMatch(String text) {
        List<Parsed> out = new ArrayList<>();
        String[] parts = text.toLowerCase(Locale.ROOT).split(",|\\band\\b");
        for (String raw : parts) {
            String part = raw.trim();
            if (part.isEmpty()) continue;
            int qty = 1;
            Matcher m = LEADING_QTY.matcher(part);
            if (m.find()) qty = Math.max(1, Integer.parseInt(m.group(1)));
            double[] macros = DEFAULT_MACROS;
            String name = capitalize(part.replaceAll("\\d+", "").trim());
            for (Map.Entry<String, double[]> e : TABLE.entrySet()) {
                if (part.contains(e.getKey())) {
                    macros = e.getValue();
                    name = capitalize(e.getKey());
                    break;
                }
            }
            if (name.isBlank()) name = "Unknown item";
            Parsed p = new Parsed();
            p.name = name; p.qty = qty; p.macros = macros;
            out.add(p);
        }
        return out;
    }

    private static String suggestedRecipeName(String text) {
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 48) return capitalize(trimmed);
        return capitalize(trimmed.substring(0, 45)) + "...";
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }
}
